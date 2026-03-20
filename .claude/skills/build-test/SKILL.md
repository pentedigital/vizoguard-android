---
name: build-test
description: Build debug APK, run lint, and execute unit tests with JAVA_HOME configured for Android Studio JBR
---

Run the full build-test cycle for the Vizoguard Android project. Execute these three Gradle tasks sequentially and report results:

1. `assembleDebug` — Build the debug APK
2. `lintDebug` — Run Android lint checks
3. `testDebugUnitTest` — Run unit tests

Always set `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"` before running Gradle.

Report a summary table at the end:

| Step | Result |
|------|--------|
| Build | PASS/FAIL |
| Lint | PASS/FAIL (N warnings) |
| Unit Tests | PASS/FAIL (N/N passed) |

If any step fails, show the relevant error output and stop.
