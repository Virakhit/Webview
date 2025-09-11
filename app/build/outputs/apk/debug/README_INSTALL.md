Install APK to a connected Android device (Windows)

What this does:
- Script: `install_apk.bat` installs `app-debug.apk` (expects the APK in the same folder as the script).
- It auto-detects `adb` from `ANDROID_HOME`, `%LOCALAPPDATA%\Android\Sdk`, or from your PATH.

How to use (cmd.exe or Android Studio terminal):

1) Open a terminal and go to the folder containing the APK and script:

```bat
cd /d C:\Project\TestAPP\app\build\outputs\apk\debug
```

2) Run the installer (basic):

```bat
install_apk.bat
```

3) If you have multiple devices/emulators connected, pass the device serial (from `adb devices`):

```bat
install_apk.bat emulator-5554
```

4) To pass extra install flags (e.g., allow downgrade):

```bat
install_apk.bat emulator-5554 "-r -d"
```

Notes / Troubleshooting:
- If `adb` is not found, install Android SDK Platform-tools and either add `platform-tools` to PATH or set the `ANDROID_HOME` environment variable.
- If you get signature mismatch errors, uninstall the existing app first:

```bat
adb uninstall <package.name>
```

- If `adb devices` shows no devices: enable USB debugging on the device, check the USB cable, accept the RSA prompt on the device, and install device drivers on Windows if needed.
