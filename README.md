# Eminence VOD for Android TV

Eminence VOD opens [Dulo.mov](https://dulo.mov/) in an Android WebView and uses the site's player.

## Install and use

Download the APK from [GitHub Releases](https://github.com/ronanoliv/EminenceVOD/releases).
On your TV, use Downloader or transfer the APK over USB, then open the file to install it.

- Move the pointer with the remote's arrows and select with OK.
- Hold Up or Down at the edge of the screen to scroll. Channel Up and Down also scroll where available.
- Press Up again at the top edge or press Menu to open the toolbar. Press Down or Back to return to the page.
- Press Back to leave fullscreen video or return to the previous page.

## Build

Use Java 17, Gradle 8.9, Android SDK 35 and Build Tools 35.0.0.
Run `gradle :app:lintDebug :app:assembleDebug :app:assembleRelease`.
The **Build Eminence VOD APK** GitHub Actions workflow verifies the debug APK and uploads both build variants. Sign the unsigned release APK with the release signing key before distribution.
