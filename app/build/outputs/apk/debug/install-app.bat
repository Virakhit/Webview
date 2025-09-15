@echo off
setlocal enabledelayedexpansion

:: install-app.bat
:: Usage: run this from the folder containing the APK output. It will
:: - find the APK via output-metadata.json (fallback: first .apk in folder)
:: - locate adb (PATH, ANDROID_HOME, ANDROID_SDK_ROOT, common user SDK)
:: - install the APK (-r)
:: - try to launch the app using adb shell monkey if package id is available

set APK_DIR=%~dp0
set METADATA=%APK_DIR%output-metadata.json

echo Looking for output metadata at "%METADATA%" ...
if not exist "%METADATA%" (
	echo Metadata file not found. Looking for any .apk in the folder...
	for %%f in ("%APK_DIR%*.apk") do (
		set "APK_FILE=%%~nxf"
		goto found_apk
	)
	echo No APK found in %APK_DIR%
	goto end
)

:: Parse outputFile from JSON using PowerShell
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "Try { $j = Get-Content -Raw -Path '%METADATA%' | ConvertFrom-Json; if ($j.elements -and $j.elements[0].outputFile) { Write-Output $j.elements[0].outputFile } } Catch { Exit 1 }"`) do (
	set "APK_FILE=%%~A"
)

if not defined APK_FILE (
	echo Could not parse APK filename from metadata; falling back to searching for .apk files...
	for %%f in ("%APK_DIR%*.apk") do (
		set "APK_FILE=%%~nxf"
		goto found_apk
	)
	echo No APK found.
	goto end
)

:found_apk
echo Found APK: %APK_FILE%

:: Try to extract package name (applicationId) from metadata
set "PACKAGE="
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "Try { $j = Get-Content -Raw -Path '%METADATA%' | ConvertFrom-Json; if ($j.applicationId) { Write-Output $j.applicationId } } Catch { Exit 0 }"`) do (
	set "PACKAGE=%%~P"
)

if defined PACKAGE (
	echo Package id from metadata: %PACKAGE%
)

:: Locate adb
set "ADB="
where adb >nul 2>nul
if %ERRORLEVEL% EQU 0 (
	for /f "delims=" %%a in ('where adb') do set "ADB=%%a" & goto adb_found
)

if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" set "ADB=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe"

:adb_found
if not defined ADB (
	echo adb not found. Please ensure Android Platform-Tools are installed and adb is on PATH, or set ANDROID_HOME/ANDROID_SDK_ROOT.
	goto end
)

echo Using adb at: %ADB%

:: Check for connected devices/emulators
echo Checking for connected devices/emulators...
%ADB% devices | findstr /R "device$" >nul
if %ERRORLEVEL% NEQ 0 (
	echo No device detected. If you have a device, ensure USB debugging is enabled and authorization accepted.
	echo Listing adb devices output:
	%ADB% devices
	goto end
)

:: Install the APK
set "APK_PATH=%APK_DIR%%APK_FILE%"
echo Installing "%APK_PATH%" ...
%ADB% install -r "%APK_PATH%"
set "INSTALL_RC=%ERRORLEVEL%"
if %INSTALL_RC% EQU 0 (
	echo Install succeeded.
) else (
	echo Install failed with exit code %INSTALL_RC%.
	goto end
)

:: Try to launch the app if package known
if defined PACKAGE (
	echo Attempting to launch main activity via monkey for package %PACKAGE% ...
	%ADB% shell monkey -p %PACKAGE% -c android.intent.category.LAUNCHER 1
	echo Launch command sent.
)

:end
endlocal
echo Done.

