---
name: release-apk
description: Build signed release APK with ProGuard and verify output
disable-model-invocation: true
---

Build a release APK for the Vizoguard VPN app. Steps:

1. **Pre-checks**:
   - Verify `app/proguard-rules.pro` exists
   - Check `versionCode` and `versionName` in `app/build.gradle.kts` — confirm with user if they want to bump

2. **Build**:
   - Run `./gradlew assembleRelease`

3. **Verify output**:
   - Check APK exists at `app/build/outputs/apk/release/`
   - Report file size
   - Run `./gradlew lintRelease` to check for release-specific warnings

4. **Report**:

| Item | Value |
|------|-------|
| Version | versionName (versionCode) |
| APK Size | N MB |
| ProGuard | Enabled |
| Lint | PASS/FAIL |
| Output | path/to/apk |

Note: Release builds require a signing config. If no signing config is set up, the build will produce an unsigned APK. Remind the user to sign it before distribution.
