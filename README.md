# WebViewApp

Simple Android app that shows a first-launch setup screen to collect `companyId`, `brandId`, and `outletId`, then loads the composed URL in a WebView:

`https://cyberforall.net/GivyFE/<companyId>,<brandId>,<outletId>`

## How it works
- First launch: `SetupActivity` asks for the three IDs and stores them in SharedPreferences.
- Next launches: `MainActivity` loads the WebView directly with the saved IDs.
- Back button navigates back in WebView history, and exits if no history.

## Build and run
Open the folder in Android Studio (Giraffe+), let it sync Gradle, and run the `app` configuration on a device/emulator with internet access. Min SDK 24.

## Notes
- JavaScript and DOM storage are enabled in the WebView.
- Internet permission is declared in the manifest.
