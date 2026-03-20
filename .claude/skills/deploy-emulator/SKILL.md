---
name: deploy-emulator
description: Build, install on Android emulator, launch app, and take a screenshot
disable-model-invocation: true
---

Deploy the Vizoguard VPN app to the Android emulator and verify it launches. Steps:

1. Set environment:
   - `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`
   - `ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"`

2. Check if emulator is running:
   - Run `adb devices` and look for `emulator-*` entries
   - If no emulator running, list AVDs with `emulator -list-avds` and start the first one
   - Wait for the device to be ready

3. Build and install:
   - Run `./gradlew installDebug`

4. Launch the app:
   - `adb shell am start -n com.vizoguard.vpn/.MainActivity`

5. Take a screenshot after 2 seconds:
   - `adb exec-out screencap -p > emulator_screenshot.png`
   - Show the screenshot to the user

6. Check for crashes:
   - `adb logcat -d --pid=$(adb shell pidof com.vizoguard.vpn) "*:E"` — filter for errors
   - Report any app crashes or errors found
