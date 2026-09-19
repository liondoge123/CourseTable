# Release conventions

CourseTable releases follow semantic versioning and use GitHub-friendly artifact names.

## Versioning

- Git tag: `v{versionName}`
- Release title: `CourseTable v{versionName}`
- `versionName`: semantic version in `MAJOR.MINOR.PATCH` format
- `versionCode`: a positive integer incremented for every published Android release
- Breaking changes increment `MAJOR`, backward-compatible features increment `MINOR`, and fixes or UI refinements increment `PATCH`

## Android artifacts

APK files use this format:

`CourseTable-v{versionName}-{ABI}-{buildType}.apk`

Examples:

- `CourseTable-v1.2.23-arm64-v8a-release.apk`
- `CourseTable-v1.2.23-armeabi-v7a-release.apk`
- `CourseTable-v1.2.23-universal-release.apk`

Release artifacts must be signed and have their package name, version, ABI, and checksum verified before publishing.

Formal APKs, including the universal build, target approximately 30 MB. Small overruns are acceptable; the packaging script uses a 31,500,000-byte guard (5% headroom) to prevent substantial growth before handoff.
The universal build contains arm64-v8a and armeabi-v7a. Native libraries are compressed in APKs and extracted by Android during installation.

## Automated builds

Use the project script for both preview packages and formal releases:

```powershell
# Fast test package: increment the internal build number and build arm64 only.
.\scripts\build-apk.ps1 preview

# Formal release: run the full validation and build every configured ABI.
.\scripts\build-apk.ps1 release

# Override the candidate semantic version when needed.
.\scripts\build-apk.ps1 release -VersionName 1.6.0
```

Preview APKs use the same package name and signing certificate as formal releases, so
they update the installed app. Their unique `preview-b{versionCode}` filename avoids
file-transfer caching.

The two modes intentionally use different validation levels:

| Mode | ABI output | Tests / Lint | R8 | Verification |
| --- | --- | --- | --- | --- |
| `preview` | arm64-v8a only | Skipped | Disabled | Package, version, SDK, ABI, signature, SHA-256 |
| `release` | arm64-v8a, armeabi-v7a, universal | Full | Enabled | Package, version, SDK, ABI, signature, SHA-256 |

Gradle output is written to `app/build/logs/{mode}-b{versionCode}.log` instead of being
streamed to the console. A successful build prints only the artifact summary; a failed
build prints the last 80 log lines and the full log path. Both modes copy only the
arm64 APK to the project root.
