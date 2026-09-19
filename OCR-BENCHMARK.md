# OCR evaluation and integration — 2026-09-15

The initial experiment kept alternative engines outside the APK. After the user approved an approximately 30 MB formal-package budget, the application integrates **PP-OCRv6 Tiny recognition only**, using ML Kit element boxes and Android bitmap resizing instead of OpenCV or a second detector. The recognition model is bundled; the detection/orientation models and Python environment remain in ignored `.cache/ocr-benchmark/`. Contrast preprocessing and screenshot fixtures remain under `androidTest`.

## Result on the supplied screenshot

| Local engine/view | Course-name content matches | Complete meeting-text matches |
| --- | ---: | ---: |
| ML Kit, original cell crops | 5 / 11 | 9 / 12 |
| ML Kit, Otsu-based contrast cell crops | 1 / 11 | 7 / 12 |
| RapidOCR 3.9.2, PP-OCRv6 Small | 10 / 11 | 11 / 12 |
| RapidOCR 3.9.2, PP-OCRv6 Tiny | 11 / 11 | 11 / 12 |
| Android ML Kit boxes + PP-OCRv6 Tiny recognition | 11 / 11 | 12 / 12 |

These are raw OCR field matches on **one** screenshot, not whole-import success rates or evidence of general accuracy. All engines use the same manually annotated cell crops. Course-name scoring normalizes Unicode and ignores punctuation/spacing; Tiny still misses a closing parenthesis. Meeting scoring preserves range hyphens and week-list separators: `12节` does not match `1-2节`. `、`, comma and period list separators are normalized. Ground truth retains the screenshot's visible `形势与政策U` suffix. Teachers and locations were not scored. ML Kit ran on the Android emulator; RapidOCR ran on the host, so elapsed times are not comparable.

The Python Tiny pipeline reads the first weekday as `星期月一`. The Android recognition-only integration reads this field correctly in the cell benchmark. Neither result establishes general accuracy. No production spelling replacements or fixture-specific parser rules were introduced. Low-confidence predictions and disagreements between engines on temporal fields are marked for review.

## Size decision

- Small model files: 31,749,509 bytes (detection + recognition + orientation).
- Tiny model files: 6,904,963 bytes (detection + recognition + orientation).
- Standard `onnxruntime-android:1.21.1` arm64 native files: 18,009,128 bytes before APK packaging. This is additional to model data and Java code; it is not a measured APK increase.
- In the initial isolated experiment, before/after arm64 Debug APKs were **29,825,266 bytes**, increase **0 bytes**, with identical ZIP payload entries. This is not the size of the integrated application.

The integrated recognition model is 4,489,813 bytes, SHA-256 `e16e242de5937ad92609223f19bc2aff3727ee40b095f996907c24749bad251b`. Do not bundle the full second detection pipeline or enable the contrast filter. Compress APK native libraries (`useLegacyPackaging=true`); Android extracts them on install, so installed storage is larger than download size. The universal APK contains the two supported ARM ABIs, excluding x86 libraries. The user accepts small overruns around 30 MB; the release script uses a 31,500,000-byte guard (5% headroom) before handoff. No extra archive recompression is used.

Verified formal v1.6.1 (versionCode 97) artifacts: arm64-v8a **21,008,811 bytes**, armeabi-v7a **19,665,429 bytes**, universal **30,127,030 bytes**. The release script verified package/version/ABI/signing and generated `app/build/outputs/apk/release/SHA256SUMS.txt`. Keep the full ML Kit package: removing that rule caused a release-only import failure despite passing debug tests. The staged v1.6.0 package is not suitable for handoff. The user-approved approximate budget makes this small size difference acceptable.

The signed, minified arm64 v1.6.1 package was installed on the emulator and given the original image through its real Android share/import entry. It successfully opened the original-image review dialog with **14 meeting records**, corrected course-name content, and explicit pending-review markers. No import/runtime/Tiny fallback errors appeared for that process. Evidence is saved as `app/build/benchmarks/release-smoke.xml`. This ARM package runs on an x86_64 emulator, so its complete inference timing is not representative of a native ARM phone. The unminified Android test runner cannot be injected directly into an R8-obfuscated release; the release smoke check uses the production UI entry instead.

## Reproduce from the repository root

```powershell
python -m venv .cache/ocr-benchmark/venv
.cache/ocr-benchmark/venv/Scripts/python.exe -m pip install rapidocr==3.9.2 onnxruntime==1.30.0 pillow==12.3.0
.cache/ocr-benchmark/venv/Scripts/python.exe scripts/benchmark-ocr.py
.cache/ocr-benchmark/venv/Scripts/python.exe scripts/benchmark-ocr.py --model tiny --output app/build/benchmarks/rapidocr-tiny.json

# With an Android device/emulator connected; use the project's JDK/socket configuration.
.\gradlew.bat :app:connectedDebugAndroidTest -PcourseTableArm64Only=true '-Pandroid.testInstrumentationRunnerArguments.class=com.coursetable.app.OcrBenchmarkTest'

# Save the OcrBenchmark JSON log to app/build/benchmarks/mlkit.json, or let this
# local-workspace script retrieve the retained emulator log. Gradle uninstalls
# instrumentation apps after testing, so their files may no longer be available.
python scripts/summarize-ocr-benchmark.py
```

Raw outputs and re-scored comparisons are saved under ignored `app/build/benchmarks/`. Clear the emulator's `OcrBenchmark` log before each independent experiment to avoid retrieving an older result.

Reference implementations: [RapidOCR](https://github.com/RapidAI/RapidOCR), [PaddleOCR Android SDK](https://www.paddleocr.ai/main/version3.x/inference_deployment/cross_platform/android_deployment.html). Model/runtime byte counts above were measured from downloaded files, not inferred from project claims.
