# PayUp

PayUp is an app for home poker games. It tracks who bought in and for how much, handles cash-outs at the end, and works out who pays whom, with the fewest transfers. It runs as an Android app and in the browser, and a host can share a live link so friends follow the game from any phone.

## Download

**Android** (Android 8.0 or newer):

1. Open the **[latest release](https://github.com/yorairh/payup/releases/latest)** and download **`PayUp-<version>.apk`**.
2. Open the downloaded file. Android will ask you to allow installing apps from this source ("install unknown apps"): allow it once, then tap **Install**.

New versions install over the old one and keep your games. Always download the newest release.

**Web (including iPhone):** open **https://payup.web.app**. On an iPhone (iOS 18.2 or newer), open it in Safari and use Share → Add to Home Screen to install it. Games are saved on that device only.

## What you can do

- **Run a game:** add players, record buy-ins and rebuys (with or without chips), deal seats, and cash out at the end. The app tells you who pays whom.
- **Share a live game:** tap the share icon on the table screen. A QR code and a link let friends watch the pot and everyone's buy-ins and cash-outs live, from any phone or browser, with nothing to install. They only watch; you edit.
- **Hand over hosting:** give the host link to someone else if you have to leave. They take over the game and you keep watching it.
- **Watch a game:** open a game link, or use "Watch a game" on the home screen. When the game ends it is saved into your history.
- **History and leaderboard:** every finished game is kept, with a leaderboard across games.
- **Share the result** as text or as a picture.
- **Backup:** Settings → Backup creates a file, and Restore reads it on Android or on the web, so you can move games between devices.
- **9 languages:** English, Hebrew, Spanish, Russian, Arabic, French, German, Portuguese and Chinese, with right-to-left layout.

## The web app

The web app at **https://payup.web.app** is the same app as on Android, with the same screens and the same 9 languages, so friends on an iPhone can use it too. No account and no install from a store.

- **Browsers:** a recent Safari (iPhone with iOS 18.2 or newer), Chrome, Edge or Firefox. An older browser shows an "update your browser" page.
- **Install it:** on an iPhone, Share → Add to Home Screen. On Android or desktop Chrome, use the browser's "Install app" option. It then opens like an app and works without a connection after the first load.
- **Your games stay on the device and browser** where you played them. Clearing the site data in the browser deletes them, so use Settings → Backup now and then. Only one tab can have PayUp open at a time.
- **Sharing works the same:** host a game from the web or from Android, and watch it from either. Game links look like `payup.web.app/g/…`.
- **Language:** it follows the browser's language, and you can change it in Settings. Add `?lang=he` (or another language code) to the address to try another language.
- **Updates:** the site updates itself. Reload the page once if you don't see a new version.

## Good to know

- Only games you tap Share on go online. Everything else stays on your device.
- Anyone who has a game's link can watch it, and anyone who has its host link can take it over, so share the host link only with people you trust. A game stays online until you delete it from your history.
- Your history, leaderboard and friends stay on each device.
- The version you have is shown in the bottom left corner of the home screen.
- Found a bug or have an idea? Tell the person who sent you the app.

## For developers

Built with Kotlin Multiplatform and Compose Multiplatform: one shared codebase for the Android app and the web app (WebAssembly). Games are stored with Room, and shared games live in Firebase (Firestore and Hosting). The architecture and the commands are in [CLAUDE.md](CLAUDE.md), the web and sharing plan is in [docs/WEB_ROADMAP.md](docs/WEB_ROADMAP.md) and [docs/SHARED_GAMES.md](docs/SHARED_GAMES.md), and release notes are in [docs/releases](docs/releases).

Build (needs the Android SDK via Android Studio, and JDK 17 or newer; Android Studio's bundled JDK works):

```bash
./gradlew assembleDebug :shared:testAndroidHostTest
```

The web app is built with `./gradlew :webApp:wasmJsBrowserDistribution`. The APK and the site are written outside the project folder (see CLAUDE.md for why).
