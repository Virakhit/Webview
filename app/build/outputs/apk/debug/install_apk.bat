@echo off
rem install_apk.bat - Installs app-debug.apk to a connected Android device or emulator
rem Usage: install_apk.bat [device_serial] [install_flags]
rem Example: install_apk.bat                 -> installs to the single connected device using -r
rem          install_apk.bat emulator-5554   -> installs to emulator-5554 using -r
rem          install_apk.bat emulator-5554 "-r -d" -> install, allow downgrade

setlocal EnableDelayedExpansion

rem APK is expected to be in this folder (same folder as the script)
set "APK=%~dp0app-debug.apk"
if not exist "%APK%" (
  echo ERROR: app-debug.apk not found in %~dp0
  pause
  exit /b 1
)

rem Find adb: prefer ANDROID_HOME, then LOCALAPPDATA SDK, then PATH
set "ADB="
if defined ANDROID_HOME (
  if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
)
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB (
  where adb >nul 2>&1 && set "ADB=adb"
)
if not defined ADB (
  echo ERROR: adb not found. Install Android SDK Platform-tools and ensure adb is on PATH or set ANDROID_HOME.
  pause
  exit /b 2
)

rem Parse args
set "SERIAL_ARG="
set "FLAGS=-r"
if "%~1" neq "" (
  set "SERIAL_ARG=-s %~1"
)
if "%~2" neq "" (
  set "FLAGS=%~2"
)

echo Using adb: %ADB%
echo APK: %APK%

"%ADB%" devices
echo.

echo Installing... (flags: %FLAGS%)
"%ADB%" %SERIAL_ARG% install %FLAGS% "%APK%"
set "RC=%ERRORLEVEL%"
if %RC% equ 0 (
  echo Install succeeded.
) else (
  echo Install failed with code %RC%.
  echo Common fixes:
  echo  - If signature mismatch: adb uninstall <package> then re-run install.
  echo  - If version downgrade: add -d flag ("-r -d").
)
pause
endlocal
