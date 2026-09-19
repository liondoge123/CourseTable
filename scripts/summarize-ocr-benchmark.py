"""Score raw benchmark outputs. Keep '-' and ',' when comparing time fields."""
import importlib.util
import json
import subprocess
import shutil
import os
from pathlib import Path

spec = importlib.util.spec_from_file_location("benchmark", Path(__file__).with_name("benchmark-ocr.py"))
benchmark = importlib.util.module_from_spec(spec)
spec.loader.exec_module(benchmark)
expected = json.loads(Path("app/src/androidTest/assets/selection-details.expected.json").read_text(encoding="utf-8"))
rapid = json.loads(Path("app/build/benchmarks/rapidocr.json").read_text(encoding="utf-8-sig"))
mlkit_path = Path("app/build/benchmarks/mlkit.json")
if not mlkit_path.exists():
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk and Path("local.properties").exists():
        sdk = next((line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\") for line in Path("local.properties").read_text().splitlines() if line.startswith("sdk.dir=")), None)
    adb = shutil.which("adb") or (str(Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")) if sdk else None)
    if not adb:
        raise SystemExit("Set ANDROID_SDK_ROOT, add adb to PATH, or save OcrBenchmark JSON to app/build/benchmarks/mlkit.json")
    logs = subprocess.check_output([str(adb), "logcat", "-d", "-s", "OcrBenchmark:I"], encoding="utf-8")
    records = [line.split("I OcrBenchmark: ", 1)[1] for line in logs.splitlines() if "I OcrBenchmark: {" in line]
    if not records:
        raise SystemExit("Run OcrBenchmarkTest first or save its JSON log to app/build/benchmarks/mlkit.json")
    mlkit_path.write_text(records[-1], encoding="utf-8")
mlkit = json.loads(mlkit_path.read_text(encoding="utf-8-sig"))
results = {}
inputs = [("RapidOCR Small", rapid), ("ML Kit original", mlkit["original"]), ("ML Kit contrast", mlkit["contrast"])]
if "tiny" in mlkit:
    inputs.append(("Android ML Kit boxes + Tiny recognition", mlkit["tiny"]))
tiny_path = Path("app/build/benchmarks/rapidocr-tiny.json")
if tiny_path.exists():
    inputs.append(("RapidOCR Tiny", json.loads(tiny_path.read_text(encoding="utf-8-sig"))))
for name, raw in inputs:
    fields = {}
    for key in ("names", "meetings"):
        pairs = list(zip(raw[key], expected[key]))
        matches = sum(benchmark.normalized(a, key == "meetings") == benchmark.normalized(b, key == "meetings") for a, b in pairs)
        fields[key] = {"matches": matches, "total": len(pairs), "mismatches": [{"expected": b, "actual": a} for a, b in pairs if benchmark.normalized(a, key == "meetings") != benchmark.normalized(b, key == "meetings")]}
    results[name] = fields
print(json.dumps(results, ensure_ascii=False, indent=2))
Path("app/build/benchmarks/comparison.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
