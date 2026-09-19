"""Development-only RapidOCR benchmark. Dependencies/models are never Android assets.

python -m venv .cache/ocr-benchmark/venv
.cache/ocr-benchmark/venv/Scripts/python -m pip install rapidocr onnxruntime pillow
.cache/ocr-benchmark/venv/Scripts/python scripts/benchmark-ocr.py
"""
import argparse
import importlib.metadata
import json
import time
import unicodedata
from pathlib import Path


def normalized(text, schedule=False):
    text = unicodedata.normalize("NFKC", text).replace("、", ",").replace(".", ",").replace("～", "-").replace("~", "-").replace("—", "-")
    return "".join(c for c in text if c.isalnum() or schedule and c in "-,")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", type=Path, default=Path("app/src/androidTest/assets/selection-details.jpg"))
    parser.add_argument("--expected", type=Path, default=Path("app/src/androidTest/assets/selection-details.expected.json"))
    parser.add_argument("--output", type=Path, default=Path("app/build/benchmarks/rapidocr.json"))
    parser.add_argument("--model", choices=("small", "tiny"), default="small")
    args = parser.parse_args()
    from PIL import Image
    from rapidocr import RapidOCR, ModelType
    import numpy as np

    image = Image.open(args.image).convert("RGB")
    expected = json.loads(args.expected.read_text(encoding="utf-8"))
    started = time.perf_counter()
    model = ModelType(args.model)
    engine = RapidOCR(params={"EngineConfig.onnxruntime.intra_op_num_threads": 2, "EngineConfig.onnxruntime.inter_op_num_threads": 1, "Det.model_type": model, "Rec.model_type": model})
    full = engine(np.array(image))
    full_lines = [{"text": text, "box": box.tolist(), "score": float(score)} for text, box, score in zip(full.txts, full.boxes, full.scores)]
    names = []
    for top, bottom in zip(expected["rowEdges"], expected["rowEdges"][1:]):
        result = engine(np.array(image.crop((292, top + 2, 489, bottom - 2))))
        names.append("".join(result.txts or []))
    meetings = []
    for top in expected["meetingTops"]:
        result = engine(np.array(image.crop((988, top, 1252, top + 30))))
        meetings.append("".join(result.txts or []))
    package = Path(importlib.metadata.distribution("rapidocr").locate_file("rapidocr"))
    models = [{"name": p.name, "bytes": p.stat().st_size} for p in package.rglob("*.onnx") if args.model in p.name or "cls" in p.name]
    report = {
        "engine": "RapidOCR", "version": importlib.metadata.version("rapidocr"), "model": args.model,
        "scope": "one supplied screenshot; normalized exact field matches, not a general accuracy estimate",
        "seconds": round(time.perf_counter() - started, 3), "modelFiles": models,
        "nameMatches": sum(normalized(a) == normalized(b) for a, b in zip(names, expected["names"])),
        "meetingMatches": sum(normalized(a, True) == normalized(b, True) for a, b in zip(meetings, expected["meetings"])),
        "names": names, "meetings": meetings, "fullImage": full_lines,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k not in ("fullImage",)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
