# PayUp — web version and shared game links (roadmap)

## Context
Friends with iPhones can't install the Android app, and the user doesn't want the App Store. Goal: the same app runs in the browser (installable to the iPhone home screen), and a host can share a live game link that friends watch from any phone. To get there, the code is first split into shared Kotlin (logic, data, screens) and thin Android/web shells. This file is the task backlog. Tasks are done one at a time, each with its own questions first; tick the box when a task is committed.

## Decisions (agreed)
| Topic | Decision |
|---|---|
| Android app | Kept; Android and web share one Kotlin codebase |
| Web UI | Kotlin Multiplatform + Compose Multiplatform (same screens on both). No language change needed |
| Browsers | Modern web build only: iPhone needs iOS 18.2+; older Safari gets an "update iOS" page |
| Online service | Firebase: Firestore (live data), Hosting (free `*.web.app` address), anonymous auth. Free Spark plan |
| Shared link | Only the host edits; anyone with the link watches live. No sign-in (invisible anonymous ID) |
| Host handover | Separate secret host link; one host at a time, the old host becomes a viewer |
| What goes online | Only games the host taps Share on; everything else stays offline as today |
| History / leaderboard / friends | Per device. A shared game a viewer watched is saved into their history when it finishes |
| Retention | Online copy stays until the host deletes the game |
| Sharing at the table | QR code, share sheet, copy link |
| Android + links | Links open in the app when installed (App Links), otherwise in the browser |
| iPhone storage | Installable web app (PWA) + "Add to Home Screen" hint + `storage.persist()` |
| Order | Code split → full web app (local only) → shared links |
| Releases | One release per milestone (0.3.0, 0.4.0, 0.5.0); tasks committed one by one |
| Web deploy | GitHub Action deploys the website on each `vX.Y.Z` tag. APK stays a local release step (the signing key never leaves this PC) |

## Tech stack (from research, Nov 2026; re-check versions when each task starts)
- Kotlin 2.4.x, AGP 9 (shared code needs `com.android.kotlin.multiplatform.library`; app becomes its own module), KSP2, Compose Multiplatform 1.12.2 → 1.13 when stable (it carries iPhone Safari text-input fixes).
- Database: **Room 3** (`androidx.room3`, KMP incl. web via SQLite-WASM in a Web Worker on OPFS). Keeps the Android database file, name, version and schemas.
- Navigation 3 (sealed `Route` keys), lifecycle ViewModel KMP, Compose resources (strings + plurals), kotlinx-datetime, DataStore KMP or multiplatform-settings.
- Firebase: a `GameSync` interface in common code. Android uses the native Firebase SDK; web uses small hand-written bindings to the Firebase JS SDK (GitLive's KMP SDK has wasm support only in alpha).
- Module layout: `:shared` (commonMain / androidMain / webMain, schemas) + `:androidApp` (activity, manifest, signing, locale config) + web entry (index.html, PWA manifest, service worker, SQLite worker).

## Milestone 0 — prepare (before touching the code layout)
- [x] **1.** **Save this roadmap** into the repo as `docs/WEB_ROADMAP.md`, with a checkbox per task.
- [x] **2.** **Backup / restore of games (JSON export and import)** on Android, from Settings. It protects your real games during the database upgrade and later lets you move games to the web. Done: plain JSON file (format `payup-backup`, version 1), merge-only restore with a preview, one transaction. Still to confirm on a phone.
- [x] **3.** **Firebase project.** You create it in the Firebase console with your Google account (I can't create accounts). Then I connect Hosting with a preview channel for testing. Done: project `payup-ae6fc` (free Spark plan, Analytics off), Hosting site `payup-ae6fc.web.app` with nothing deployed live yet. `firebase.json` + `.firebaserc` + `web-placeholder/` are in the repo; a test page was deployed to a 7-day preview channel. Deploy a new preview with `firebase hosting:channel:deploy test --expires 7d`. Firestore and anonymous sign-in are set up later (task 22+). There is also an unused second project `payup-b239b` in the same account.
- [x] **4.** **Spike A: web on iPhone (throwaway branch).** Done: deployed, and it works on a real iPhone (plus Android Chrome and desktop Brave). See `docs/SPIKE_A_RESULTS.md`. A tiny Compose web page with:
  - Hebrew, Arabic and English text fields and money input;
  - right-to-left layout;
  - Russian and Arabic plurals from Compose resources (check that `values-iw` works for browser Hebrew `he`);
  - bundled Hebrew, Arabic and Chinese fonts.

  Deploy it to the preview channel, and you try it on an iPhone (and Android Chrome). Also measure first-load size and time on mobile data.
- [x] **5.** **Spike B: Room 2.6 → Room 3 upgrade.** Done (branch `spike-b-room3`, never merged): builds, 106 tests pass, schema identity hash identical, and the phone test passed (Room 3 opened the Room 2.6.1 database with all games). See `docs/SPIKE_B_RESULTS.md`. On a branch, port the database and its 4 migrations, then install a release-signed build over 0.2.0 on your phone with real games. Do a backup first (task 2).
- [x] **6.** **Go/no-go checkpoint (your call).** Decided GO on 2026-11-06: both spikes passed (Compose web works on a real iPhone; Room 3 opens the existing database). If Spike A fails on iPhone, we decide together: wait for a newer Compose, or build a separate web UI.

## Milestone 1 — code split, no visible change (release 0.3.0)
- [x] **7.** (Done together with task 9, phone-tested over 0.2.0.) Upgrade the toolchain (Kotlin 2.4, AGP 9, KSP2, current Jetpack Compose) while still Android-only, and do a phone check. **Do this together with task 9:** Room 2.6.1 does not build on KSP 2 (Spike B), so the toolchain and Room 3 move in one step.
- [x] **8.** (Done: 108 tests pass, shared code also compiles for the web. Replaced `Integer.numberOfTrailingZeros` in `Settlement.kt` too, which the web compile caught.) Create `:shared` (KMP, Android target only for now) and `:androidApp`. Move `domain/` and its tests to commonMain/commonTest. Replace `java.time` in `Leaderboard.kt` with kotlinx-datetime.
- [x] **9.** Done: Room 3 (with task 7) and the database now lives in `:shared`, which also compiles for the web with Room 3. Only the Android builder stays in `:androidApp`. Move the database to Room 3 in `:shared`. DAOs become suspend/Flow, migrations are ported, and the database name, version and schemas stay the same.
- [x] **10.** (Done: multiplatform-settings, same "settings" file and keys on Android so values carry over; 8 new tests.) Replace `ThemeStore` and `CurrencyStore` (SharedPreferences) with multiplatform settings, migrating the existing values.
- [x] **11.** (Done: Compose resources in `:shared`, `values-he`, `AppLanguage` interface with the Android side, `StringParityTest`. Note for task 15: on the web `stringResource` loads asynchronously, so strings appear after the first frame unless they are preloaded.) Move strings to Compose resources: 9 languages with plurals, plus a cross-platform language override. Android keeps the system per-app language. Add the string-parity check to the repo.
- [x] **12.** (Done: ViewModels are in `:shared`, `AppDependencies` replaces the `PayUpApp` cast, typed `Route` keys with JetBrains Navigation 3, same fade between screens. The backup file is read by the Android screen and handed to `BackupViewModel` as text.) Move ViewModels to lifecycle KMP, and the string routes to Navigation 3 with typed routes.
- [x] **13.** (Done: all screens, theme and common code are in `:shared`; the platform pieces are interfaces in `LocalPlatform` instead of expect/actual, with Android implementations in `:androidApp`. The share image is still drawn on an Android bitmap. The "backup saved" Toast became a snackbar.) Move the screens to commonMain. The platform pieces become small interfaces:
  - share text and share image (the image is currently drawn with Android Canvas);
  - date and time formatting;
  - system bars and the back button;
  - opening links.
- [ ] **14.** (Deferred by the user on 2026-11-07: the 0.3.0 release candidate was built and nothing was pushed or tagged; do the release later, then the version in `androidApp/build.gradle.kts` is still 0.2.0 / code 2. Continue with milestone 2 first.) Release 0.3.0, after a full phone regression check (every screen, every language, upgrade over 0.2.0 keeps the games).

## Milestone 2 — the web app, local only (release 0.4.0)
- [x] **15.** (Done: new `:webApp` module with the entry point, `index.html` loading screen and the "update your browser" page (English and Hebrew); five bundled fonts (Hebrew, Arabic, Chinese subset, emoji subset, card-suit symbols; licences and what was cut are in `webApp/font-licenses/`), and a gate that shows nothing until the fonts and strings are loaded. The screen is a placeholder shell; the real screens come in tasks 16 and 17. Checked in the built-in browser in English, Hebrew and Chinese. First load is about 4.7 MB gzipped (the app code is 3.3 MB of it); nothing is requested from Google. The browser does not tell Compose the layout direction, so the shell derives it from the language; task 17 replaces that with the language picker. Not yet seen on a real iPhone.) Add the web target and entry point: `index.html`, a loading screen, bundled fonts, and an "update your iOS" page for unsupported browsers.
- [ ] **16.** Web storage: Room 3 on the SQLite web worker for games, browser storage for settings.
- [ ] **17.** Web platform pieces:
  - language from the browser plus the in-app picker;
  - share through the Web Share API, or a download fallback;
  - browser back and forward;
  - right-to-left layout.
- [ ] **18.** PWA: manifest and icons, offline service worker, the iPhone "Add to Home Screen" hint, and `storage.persist()`.
- [ ] **19.** Import games from an Android backup (task 2) into the web app. *(Optional, decided in that task.)*
- [ ] **20.** A GitHub Action deploys the website to Firebase Hosting on each release tag. This means storing a Firebase deploy credential as a GitHub secret.
- [ ] **21.** Release 0.4.0: test the web app in the built-in browser here, then on your iPhone and Android Chrome.

## Milestone 3 — shared game links (release 0.5.0)
- [ ] **22.** Design the online game data: a random unguessable game ID, the host's anonymous ID, and a secret host key. Local database migration 4→5 adds the shared ID and host flag.
- [ ] **23.** Firestore security rules:
  - anyone with the ID can read;
  - only the current host can write;
  - a takeover needs the host key;
  - games can't be listed;
  - plus tests for the rules on the Firebase emulator.
- [ ] **24.** Add the `GameSync` interface in common code, with an Android implementation (Firebase SDK) and a web implementation (Firebase JS SDK). Both sign in anonymously.
- [ ] **25.** Host flow: a Share button on the table screen uploads the game and shows the QR code, share sheet and copy link. The host's later edits sync live, and a status shows offline or syncing.
- [ ] **26.** Viewer flow: opening a link shows the game read-only and live (table, cash-out, result). When the game finishes, it's saved into the viewer's history once, without duplicates.
- [ ] **27.** Host handover: opening the host link takes over editing, and the previous host turns into a viewer.
- [ ] **28.** Deleting a shared game from the host's history also deletes it online.
- [ ] **29.** Android App Links: an intent filter, plus `assetlinks.json` served from Firebase Hosting.
- [ ] **30.** Release 0.5.0: a multi-device test with an Android host, browser viewers and an iPhone viewer, a handover, and offline and reconnect.

## Verification (each task)
- `gradlew` build and all tests (common tests run for the Android and web targets).
- Every milestone release is tested on your phone, upgrading over the previous release so your saved games are checked to survive.
- Web: run locally and test in the built-in browser pane, then on the Firebase preview channel on your iPhone.
- Sharing: Firestore rules tests on the emulator, then two or more real devices.

## Risks to watch
- Compose web is still Beta, and iPhone Safari text input is being fixed every release. Spike A decides.
- Room 3 is new. Spike B proves the upgrade on your real data before Milestone 1.
- A large first web load (several MB). It's measured in Spike A and cached by the PWA afterwards.
