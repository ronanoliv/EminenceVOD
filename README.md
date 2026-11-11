# Eminence VOD for Android TV

A personal Android TV / Google TV browser launcher for https://dulo.mov/.

- Full-screen branded TV wrapper
- D-pad pointer mode (use arrows to move, OK to select)
- Menu button toggles the toolbar
- Home, Back, Reload, and external browser
- Website playback only; no scraping or stream extraction

## APK builds

GitHub Actions → **Build Eminence VOD APK** → Run workflow, or push to main. Download the **EminenceVOD-debug-apk** artifact after a successful run. Extract the ZIP to get app-debug.apk.

This is an experimental first build. Dulo compatibility and TV playback have not been verified. The original custom logo artwork still needs to be uploaded to app/src/main/res/drawable/eminence_logo.png to replace the placeholder. 

## Build locally

Install Android Studio + SDK 35 and run `gradle :app:assembleDebug`.
