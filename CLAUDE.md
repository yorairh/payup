# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

PayUp is an Android poker buy-in tracker and settlement app (Kotlin, Jetpack Compose, Room). Games are stored on the device. A host can share a game live through Firebase (Firestore and Hosting, project `payup-ae6fc`): `sync/` holds the `GameSync` seam and `SharedGameSyncer`, the Android implementation is `FirebaseGameSync`, the web one is `WebGameSync` over `webApp/firebase-bridge`, and the security rules are in `firebase/firestore.rules` (design in `docs/SHARED_GAMES.md`).

## Commands

Run from the project root in PowerShell. Set this first, because Gradle needs Android Studio's JDK:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

```powershell
.\gradlew.bat assembleDebug :shared:testAndroidHostTest :shared:compileKotlinWasmJs --console=plain -q     # build the app, run all unit tests, check the shared code still compiles for the web
.\gradlew.bat :shared:testAndroidHostTest --tests "com.payup.domain.MoneyFormatTest"     # one test class
.\gradlew.bat assembleRelease     # signed release APK
.\gradlew.bat lintDebug     # lint
```

- Web app: `.\gradlew.bat :webApp:wasmJsBrowserDistribution --console=plain -q` builds the site into `%USERPROFILE%\.payup-build\webApp\dist\wasmJs\productionExecutable`; serve that folder with any static server (a `web-dist` entry in the untracked `.claude/launch.json` does it for the in-app browser). Add `?lang=he` to the address to pretend the browser speaks another language, and `?nowasm` to see the "update your browser" page. The web build downloads its own Node.js, which is why `settings.gradle.kts` allows project repositories. Web storage: the web builder is `openWebDatabase()` in `webApp/.../WebStorage.kt`, using the Web Worker in `webApp/worker/` (a local npm package; after changing its `package.json` run `kotlinWasmUpgradeYarnLock` and commit `kotlin-js-store/wasm/yarn.lock`). Only one tab may open the database, enforced by `acquireSingleTabLock()`. The web platform pieces (dates, share, backup files, language, share picture) are in `webApp/src/wasmJsMain/kotlin/WebPlatform.kt` and `WebShareImage.kt`, and `WebApp.kt` wires them and the browser history (`navigation3-browser`) around the shared `PayUpNavHost`. A new route needs an entry in `BrowserHistory` in `WebApp.kt` as well as in `Routes.kt`. The chosen language is the `language` key in `localStorage`, applied by `index.html`. Offline: `sw.js` caches the files listed in `precache.json`, which `wasmJsBrowserDistribution` writes (see `webApp/build.gradle.kts`); `manifest.webmanifest` and `icons/` make it installable. Deploys: pushing a `vX.Y.Z` tag runs `.github/workflows/deploy-web.yml`, which tests, builds and publishes the site to Firebase Hosting (project `payup-ae6fc`, secret `FIREBASE_SERVICE_ACCOUNT_PAYUP_AE6FC`); the repo `firebase.json` serves the `web-dist` folder the workflow fills, with the cache headers. Hand-made preview deploys use a scratch folder with its own `firebase.json`.
- Lint currently fails on 2 known errors: `PropertyEscape` in the local-only `local.properties`, and `BidiSpoofing` at `shared/.../domain/MoneyFormat.kt:9`, where the source contains bidi isolate characters.
- Build output goes to `%USERPROFILE%\.payup-build\androidApp\` (and `\shared\`), not `build/`, because the Hebrew project path breaks the unit-test JVM's classpath on Windows (see the root `build.gradle.kts`). APKs are under `outputs\apk\`, reports under `reports\`, test XML under `test-results\`.
- The same Hebrew path can corrupt paths inside scripts, so run scripts from the project directory rather than passing the full path.
- `gh` is at `C:\Program Files\GitHub CLI\gh.exe` and is not on PATH.
- Only JVM unit tests exist (`shared/src/commonTest` with `kotlin.test` for `domain/`, run with `:shared:testAndroidHostTest`). There is no Robolectric or `androidTest`, so `data/` and `ui/` code is only checked on a real phone. An Android emulator exists in the SDK (`emulator.backup\emulator.exe`), which was used to test sharing; drive it with adb.

## Releases

- GitHub repo: `yorairh/payup` (public from 0.5.0). Each release is a `vX.Y.Z` tag plus a GitHub release with `PayUp-X.Y.Z.apk` attached.
- For each release, raise both `versionCode` and `versionName` in `androidApp/build.gradle.kts`.
- The repo is going **public** (done only after the last roadmap task, together with the 0.5.0 release; the visibility change itself needs the user's go-ahead at that moment). So from now on commit nothing secret or personal: no keys, tokens, signing files, local paths, emails or private data. `google-services.json` and the web Firebase config hold public identifiers only and are fine; what protects the data is `firebase/firestore.rules`. Before going public, scan the whole git history for secrets.
- Release notes live in `docs/releases/X.Y.Z.md` (like the FluRai repo), written for users in plain words: title, one line, then New / Changed / Good to know. The GitHub release is titled "PayUp X.Y.Z: short summary" and uses that file as its text. `README.md` is for users too.
- Nothing is released (tag, GitHub release, web deploy, public repo) until every roadmap task is done; test builds in between go to the user as APKs only.
- The signing key is outside the repo in `%USERPROFILE%\.payup-signing\`. If that folder is missing, release builds come out unsigned. Never commit its contents.
- Debug and release builds have different signatures, so they can't install over each other. Uninstalling to switch deletes the saved games. Send test APKs built with `assembleRelease`.

## Architecture

Three Gradle modules (`:webApp`, described below, is the web entry point with `index.html` and the bundled fonts): `:shared` (Kotlin Multiplatform: Android plus a compile-only web target; `domain/` and `data/` live here in `commonMain`: Room entities, DAO, `GameRepository`, `ThemeStore`/`CurrencyStore` over multiplatform-settings, and the schema JSON in `shared/schemas/`) and `:androidApp` (the Android-only rest: the activity and navigation host, the database builder with the Android SQLite driver, the Android `Settings` wiring in `PayUpApp`, and `platform/` with the Android implementations of the platform interfaces). The screens, theme and ViewModels are all in `:shared`. Three layers, with dependencies pointing inward: `ui/` → `data/` → `domain/`.

- **`domain/`** (in `shared/src/commonMain`) is pure Kotlin with no Android, Compose or JVM-only imports (it also compiles for the web, so no `java.*`), and it is where the unit tests are. Put game rules here: settlement, chip conversion, auto-fill, outcomes, money parsing and formatting.
- **`data/`** (in `:shared`, except the Android builder `AndroidDatabase.kt`) holds Room entities, `PayUpDao` and `GameRepository`. `GameRepository` is the only way to the database. `PayUpApp` builds one `AppDependencies` (repository, stores, language), the activity provides it as `LocalAppDependencies`, and `payUpViewModel { Vm(it.repository, gameId) }` in `ui/common/Common.kt` creates ViewModels from it. Screen arguments (the game id) come from the route and go to the ViewModel constructor. There is no DI framework.
- **`ui/`** has one package per screen, with the ViewModel usually in the same file as the screen. Navigation is Navigation 3 with the typed routes in `ui/Routes.kt` and the screen wiring in `PayUpNavHost` (`shared/.../PayUpNavHost.kt`), used by both the Android activity and the web app.

Game flow: `setup` → optional `deal` → `table` → `cashout` → `result`. A game counts as active until `finished = true`, and starting a new game discards any unfinished one.

### Patterns that span several files

- **Money** is an `Int` in hundredths (agorot, despite the currency). `Money` parses and formats numbers. `MoneyFormat` places the symbol and sign: `₪50` left to right, `50 ₪` right to left (still drawn left to right inside an isolate, so the symbol sits on the right of the number).
- **Currency** is stored per game, with symbol only and no conversion. `LocalCurrency` deliberately has no default, so a screen that shows money must wrap its content in `ProvideCurrency(game.game.currency)` or it crashes. The screen's outer composable loads the game, then provides the currency to a private `*Content` composable. The leaderboard sums across games without converting and shows the latest finished game's currency (`WithLatestCurrency`). This is an agreed product decision.
- **Cash-out** is an immutable `CashOutSession`. Each event returns a `CashOutStep` containing the new session and a list of `CashOutWrite`s, and `CashOutViewModel` stays thin and passes those writes to `GameRepository.saveCashOut`, which stores them in one transaction. Add cash-out behaviour to the session, with a test in `CashOutSessionTest`.
- **Outcomes** come from `GameOutcomes.rank`, reached through `GameWithDetails.results()`. The result screen, share text, share image and history card all use it. On a tie for first, whoever is listed first gets the crown.
- **Database schema:** bump the version in `AppDatabase`, add a `Migration`, register it in `build()`, and commit the new JSON that KSP exports to `shared/schemas/`. Migrations have no automated test.

### Translations

There are 9 languages, as Compose Multiplatform resources in `shared/src/commonMain/composeResources/`: `values` (English), `values-he` (Hebrew) and `values-iw` (an exact copy, because Android may report Hebrew as the old code `iw` while the web says `he`; the test enforces the copy), `-es`, `-ru`, `-ar`, `-fr`, `-de`, `-pt`, `-zh-rCN`. Hebrew and Arabic are right to left. The only Android string left is the launcher label `app_name` in `androidApp/src/main/res/values/strings.xml`.

- Use `stringResource(Res.string.key, args)` / `pluralStringResource(Res.plurals.key, n, n)` (`com.payup.resources.Res`, plus one import per key). Outside composables use the `suspend` `getString(...)` / `getPluralString(...)`.
- Placeholders must be positional (`%1$s`, `%1$d`), a percent sign is a single `%`, and quotes need no backslash. Compose resources do not support `%d`, `%%`, or a backslash before a quote.
- `StringParityTest` (in `:shared`, runs with the unit tests) fails the build if any language is missing a key, has different placeholders, lacks the plural forms its language needs (Russian and Arabic need extra ones), or if the language list, the folders and `locales_config.xml` disagree. When adding a language, update `AppLanguage.OPTIONS`, the folder, `locales_config.xml` and the test.
- The language picker goes through the `AppLanguage` interface (`AndroidAppLanguage` uses the system per-app language).
- Check layout direction with `Context.isRtl()`, never by testing for a specific language.
- The share image in `ShareResult.kt` draws on a bitmap by hand, so it handles right-to-left layout itself.

## Working with this project

- Web roadmap: `docs/WEB_ROADMAP.md` lists the tasks. Each milestone gets its own branch (for example `milestone-0-prepare`) with one commit per task, merged to `main` through a pull request. Roadmap tasks 1 and 2 went straight to `main`.

- Ask about missing information rather than assuming, and agree on a plan before building features.
- `FUTURE_PLANS.md` lists tester requests. When asked to "add to future features", only write it there; don't plan or build it.
