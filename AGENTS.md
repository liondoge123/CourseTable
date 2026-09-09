# CourseTable project instructions

## Release packaging

- Follow the versioning and artifact rules in `RELEASE.md` whenever creating a release build.
- Use `scripts/build-apk.ps1 preview` for installable test packages and `scripts/build-apk.ps1 release` for formal releases.
- Increment both `versionCode` and the appropriate semantic component of `versionName` for a published release.
- Name APK artifacts `CourseTable-v{versionName}-{ABI}-{buildType}.apk`.
- Do not use ambiguous suffixes such as `ui` in release artifact names.
- Verify signing, package name, version, ABI, and SHA-256 before handing off an APK.
