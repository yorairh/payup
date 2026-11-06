# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

PayUp is an Android poker buy-in tracker and settlement app (Kotlin, Jetpack Compose, Room). All data is local to the device; there is no server.

## Commands

Run from the project root in PowerShell. Set this first, because Gradle needs Android Studio's JDK:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest --console=plain -q     # build and run all unit tests
.\gradlew.bat testDebugUnitTest --tests "com.payup.domain.MoneyFormatTest"     # one test class
.\gradlew.bat assembleRelease     # signed release APK
.\gradlew.bat lintDebug     # lint
```

- Lint currently fails on 2 known errors: `PropertyEscape` in the local-only `local.properties`, and `BidiSpoofing` at `domain/MoneyFormat.kt:9`, where the source contains bidi isolate characters.
- Build output goes to `%USERPROFILE%\.payup-build\app\`, not `build/`, because the Hebrew project path breaks the unit-test JVM's classpath on Windows (see the root `build.gradle.kts`). APKs are under `outputs\apk\`, reports under `reports\`, test XML under `test-results\`.
- The same Hebrew path can corrupt paths inside scripts, so run scripts from the project directory rather than passing the full path.
- `gh` is at `C:\Program Files\GitHub CLI\gh.exe` and is not on PATH.
- Only JVM unit tests exist (`app/src/test`, JUnit 4, all under `domain/`). There is no Robolectric or `androidTest`, so `data/` and `ui/` code is only checked on a real phone. There is no emulator on this machine.

## Releases

- GitHub repo: `yorairh/payup` (private). Each release is a `vX.Y.Z` tag plus a GitHub release with `PayUp-X.Y.Z.apk` attached.
- For each release, raise both `versionCode` and `versionName` in `app/build.gradle.kts`.
- The signing key is outside the repo in `%USERPROFILE%\.payup-signing\`. If that folder is missing, release builds come out unsigned. Never commit its contents.
- Debug and release builds have different signatures, so they can't install over each other. Uninstalling to switch deletes the saved games. Send test APKs built with `assembleRelease`.

## Architecture

Three layers, with dependencies pointing inward: `ui/` → `data/` → `domain/`.

- **`domain/`** is pure Kotlin with no Android or Compose imports, and it is where the unit tests are. Put game rules here: settlement, chip conversion, auto-fill, outcomes, money parsing and formatting.
- **`data/`** holds Room entities, `PayUpDao` and `GameRepository`. `GameRepository` is the only way to the database. `PayUpApp` creates it, and `payUpViewModel { repo, handle -> ... }` in `ui/common/Common.kt` injects it, along with navigation arguments through `SavedStateHandle`. There is no DI framework.
- **`ui/`** has one package per screen, with the ViewModel usually in the same file as the screen. Navigation uses string routes, all in `MainActivity.kt`.

Game flow: `setup` → optional `deal` → `table` → `cashout` → `result`. A game counts as active until `finished = true`, and starting a new game discards any unfinished one.

### Patterns that span several files

- **Money** is an `Int` in hundredths (agorot, despite the currency). `Money` parses and formats numbers. `MoneyFormat` places the symbol and sign: `₪50` left to right, `50 ₪` right to left.
- **Currency** is stored per game, with symbol only and no conversion. `LocalCurrency` deliberately has no default, so a screen that shows money must wrap its content in `ProvideCurrency(game.game.currency)` or it crashes. The screen's outer composable loads the game, then provides the currency to a private `*Content` composable. The leaderboard sums across games without converting and shows the latest finished game's currency (`WithLatestCurrency`). This is an agreed product decision.
- **Cash-out** is an immutable `CashOutSession`. Each event returns a `CashOutStep` containing the new session and a list of `CashOutWrite`s, and `CashOutViewModel` stays thin and passes those writes to `GameRepository.saveCashOut`, which stores them in one transaction. Add cash-out behaviour to the session, with a test in `CashOutSessionTest`.
- **Outcomes** come from `GameOutcomes.rank`, reached through `GameWithDetails.results()`. The result screen, share text, share image and history card all use it. On a tie for first, whoever is listed first gets the crown.
- **Database schema:** bump the version in `AppDatabase`, add a `Migration`, register it in `build()`, and commit the new JSON that KSP exports to `app/schemas/`. Migrations have no automated test.

### Translations

There are 9 languages: `values` (English), `values-iw` (Hebrew), `-es`, `-ru`, `-ar`, `-fr`, `-de`, `-pt`, `-zh-rCN`. Hebrew and Arabic are right to left.

- Every string and plural must exist in all 9 files, and Russian and Arabic need their extra plural forms.
- Check layout direction with `Context.isRtl()`, never by testing for a specific language.
- The share image in `ShareResult.kt` draws on a bitmap by hand, so it handles right-to-left layout itself.

## Working with this project

- Ask about missing information rather than assuming, and agree on a plan before building features.
- `FUTURE_PLANS.md` lists tester requests. When asked to "add to future features", only write it there; don't plan or build it.
