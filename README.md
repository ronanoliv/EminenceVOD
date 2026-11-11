# Eminence VOD for Android TV

Eminence VOD is an Android TV catalog browser. It shows trending movies and TV shows from [TMDB](https://www.themoviedb.org/) and opens [Dulo.mov](https://dulo.mov/) in a separate in-app browser screen.

## Install and use

Download the APK from [GitHub Releases](https://github.com/ronanoliv/EminenceVOD/releases).
On your TV, use Downloader or transfer the APK over USB, then open the file to install it.

1. Select **Connect TMDB** on the home screen. Sign in to [TMDB's API settings](https://www.themoviedb.org/settings/api) and enter your **API key** or **API Read Access Token** in the TV app. The app stores the credential on the TV.
2. Use the remote to browse the trending rows, view title details, and save titles to **My List**.
3. Select **Open Dulo** to browse and play on Dulo. Search for the title on the website. TMDB metadata gives no confirmation that Dulo carries a title.

### Dulo controls

- Move the pointer with the remote's arrows and select with OK.
- Hold Up or Down at the edge of the screen to scroll. Channel Up and Down also scroll where available.
- Press Up again at the top edge or press Menu to open the toolbar. Press Down or Back to return to the page.
- Press Back to leave fullscreen video or return to the previous page.
- When a Dulo TV episode ends, the app selects the next episode if Dulo offers one.

## Build

Use Java 17, Gradle 8.9, Android SDK 35 and Build Tools 35.0.0.
Run `gradle :app:lintDebug :app:assembleDebug :app:assembleRelease`.
The **Build Eminence VOD APK** GitHub Actions workflow verifies the debug APK and uploads both build variants. Sign the unsigned release APK with the release signing key before distribution.
