---
name: gen-test
description: Generate unit tests for a Kotlin source file using the project's MockK + coroutines-test patterns
disable-model-invocation: true
---

Generate unit tests for a specified Kotlin source file in the Vizoguard Android project.

## Input

The user provides a source file path (e.g., `app/src/main/java/com/vizoguard/vpn/license/LicenseManager.kt`). If no file is specified, ask which file to test.

## Steps

1. **Read the source file** to understand its public API, dependencies, and behavior
2. **Check if a test file already exists** in the mirror path under `app/src/test/java/`
   - If it exists, read it and add missing test cases
   - If not, create a new test file

3. **Generate tests** following these project conventions:
   - Package mirrors source: `com.vizoguard.vpn.{subpackage}`
   - Use JUnit 4 (`@Test`, `Assert.*`)
   - Use MockK for mocking (`mockk()`, `every { }`, `coEvery { }`, `verify { }`)
   - Use `kotlinx.coroutines.test` for suspend functions (`runTest`, `TestScope`)
   - Use backtick test names: `` `descriptive name of what is tested` ``
   - Test both happy path and error/edge cases
   - Note: `unitTests.isReturnDefaultValues = true` — Android framework methods return defaults, not exceptions

4. **Run the tests** to verify they compile and pass:
   ```bash
   export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
   ./gradlew testDebugUnitTest --tests "com.vizoguard.vpn.**.<TestClassName>"
   ```

5. **Report results** as a summary table:

| Test | Result |
|------|--------|
| `test name` | PASS/FAIL |

## Example test structure

```kotlin
package com.vizoguard.vpn.vpn

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExampleTest {
    @Test
    fun `method returns expected result for valid input`() {
        // Arrange
        // Act
        // Assert
    }
}
```
