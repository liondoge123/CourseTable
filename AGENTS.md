# CourseTable project instructions

## Release packaging

- Follow the versioning and artifact rules in `RELEASE.md` whenever creating a release build.
- Use `pwsh scripts/build-apk.ps1 preview` for installable test packages and `pwsh scripts/build-apk.ps1 release` for formal releases (fallback to `powershell` if `pwsh` is unavailable).
- Increment both `versionCode` and the appropriate semantic component of `versionName` for a published release.
- Name APK artifacts `CourseTable-v{versionName}-{ABI}-{buildType}.apk`.
- Do not use ambiguous suffixes such as `ui` in release artifact names.
- Verify signing, package name, version, ABI, and SHA-256 before handing off an APK.

## Android device testing artifacts

- Store ADB screenshots, UI hierarchy XML, screen recordings, and other temporary device-testing files in a dedicated per-session directory under `/data/local/tmp/coursetable-testing/`. Do not place them in the phone's shared-storage root (`/sdcard/`), Downloads, Pictures, or other user-facing directories.
- Prefer streaming device output directly to the computer when supported. Store local testing artifacts in ignored directories such as `app/build/` or `.artifacts/`.
- Track the exact device paths created during the session. Pull any artifacts needed for validation to the computer, then remove the tracked device files and the session directory when testing ends, including after failures or interruptions when the device is still connected.
- Before deletion, verify every target belongs to the current test session. Never broadly delete user files or artifacts belonging to another session.
- If disconnection prevents cleanup, report the remaining exact paths and clean them up when the device is reconnected before starting another test session.

## Text input, focus, and keyboard regressions

- Read `docs/KEYBOARD_FOCUS_POSTMORTEM.md` before changing keyboard dismissal or input-field focus behavior. It records the 2026-10-02 incident and its verified fix.
- Do not globally clear focus just because IME visibility becomes false, or add an arbitrary delay to guess whether editing has ended. Keyboard visibility and editing focus are separate states.
- Preserve the agreed editor behavior: input-field taps transfer focus; blank-area and non-input-control taps end editing; controls still execute their action; scrolling, dragging, and long presses do not end editing. Outside taps must clear residual focus even if the keyboard was already manually hidden.
- Inspect the resolved Compose version and upstream fixes before attributing keyboard flicker to the system, the IME, or a container. Compose UI 1.12.0 had a text-input scheduling regression; this project moved to BOM `2026.09.00` / Compose UI 1.12.1.
- Validate keyboard continuity, not only final focus or successful typing. Run `NativeKeyboardSwitchTest` with native finger events and the real render clock, alongside `EndInputOnOutsideTapTest`. Injecting native events into a Compose simulated-clock test can itself distort input-session timing.
- Treat an unverified suspected cause as a hypothesis. Do not report a fix as complete until the intended interaction has passed relevant real-device validation.
