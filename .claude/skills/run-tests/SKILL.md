---
name: run-tests
description: Run Android unit tests and report pass/fail results with failure details
---

Run the Vizoguard Android unit tests and report results.

1. Run `./gradlew testDebugUnitTest` from the project root

2. If tests pass, parse the XML results to show a summary:

```
✓ All N tests passed
```

3. If tests fail, show:
   - Which tests failed
   - The assertion/error message for each failure
   - Suggest a fix based on the failure

Test source: `app/src/test/java/com/vizoguard/vpn/`
Results XML: `app/build/test-results/testDebugUnitTest/`
