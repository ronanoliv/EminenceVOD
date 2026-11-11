# Eminence VOD for Android TV

Eminence VOD is an Android TV catalog browser. It provides a browsable and searchable movie and TV catalog from [TMDB](https://www.themoviedb.org/) and opens playback sources in an in-app browser.

## Install and use

Download the APK from [GitHub Releases](https://github.com/ronanoliv/EminenceVOD/releases).
On your TV, use Downloader or transfer the APK over USB, then open the file to install it.

1. Select **Connect TMDB** on the home screen. Sign in to [TMDB's API settings](https://www.themoviedb.org/settings/api) and enter your **API key** or **API Read Access Token** in the TV app. The app stores the credential on the TV.
2. Use the remote arrows and **OK** to browse **Movies** or **TV Shows**, search by title, sort by newest release, and save titles to **My List**. The app loads fresh TMDB results when you open a section or page.
3. Select **Watch** to open Sweetfox. If the homepage fails to load, the app tries Flixer, Dulo, and Movy in order. **Sources** lets you select a specific website from the catalog or browser toolbar.
4. Search for the title on the chosen website. Each site handles its own player and video server selection. The in-app browser blocks new windows and keeps top-level navigation on the selected source.

### Playback controls

- Move the pointer with the remote's arrows and select with OK.
- Hold Up or Down at the edge of the screen to scroll. Channel Up and Down also scroll where available.
- Press Up again at the top edge or press Menu to open the toolbar. Press Down or Back to return to the page.
- Press Back to leave fullscreen video or return to the previous page.
- When a Dulo TV episode ends, the app selects the next episode if Dulo offers one.

## Build

Use Java 17, Gradle 8.9, Android SDK 35 and Build Tools 35.0.0.
Run `gradle :app:lintDebug :app:assembleDebug :app:assembleRelease`.
The **Build Eminence VOD APK** GitHub Actions workflow verifies the debug APK and uploads both build variants. Sign the unsigned release APK with the release signing key before distribution.
