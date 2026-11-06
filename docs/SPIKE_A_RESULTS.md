# Spike A: Compose on the web, in iPhone Safari

Roadmap task 4. A throwaway test page to answer one question before the big refactor: **is Compose Multiplatform on the web good enough for PayUp, especially Hebrew and Arabic typing on an iPhone?**

- Code: branch `spike-a-web-iphone`, folder `spikes/web/` (never merged into the app).
- Stack: Kotlin 2.4.21, Compose Multiplatform 1.12.2, Wasm only (modern browsers; iPhone needs iOS 18.2+).
- Live test page (temporary, expires **13 Nov 2026**): https://payup-ae6fc--spike-ac3nbvag.web.app/
- Redeploy: rebuild with `gradlew wasmJsBrowserDistribution` in `spikes/web`, copy the output to `dist-preview/`, then `firebase hosting:channel:deploy spike --expires 7d`.

## Status

| Part | Result |
|---|---|
| Builds and runs (desktop Chromium, phone-size window) | Done |
| Measured sizes | Done (below) |
| **iPhone Safari test** | **Still waiting for a real iPhone** (see the checklist) |
| Android Chrome test | Done by the owner on 6 Nov 2026 (Chrome on their phone) and Brave on desktop: looked fine; the only oddity was the amount splitting (finding 6) |
| Go / no-go (task 6) | After the iPhone results |

## What I verified (desktop Chromium, emulated phone window)

- **Typing works** in Hebrew, Arabic, English, mixed text and multi-line fields. Right-to-left layout mirrors correctly in `?lang=he` and `?lang=ar`.
- **The app's own money code runs unchanged** in the browser: `Money` and `MoneyFormat` give `₪18.5`, `−$18.5`, `+€18.5` left to right and `18.5 ₪`, `−18.5 $`, `+18.5 €` right to left. The decimal input filter works.
- **Plurals are correct** for Russian (1 игрок, 2 игрока, 5 игроков, 11 игроков, 21 игрок, 100 игроков) and Arabic (5 لاعبين, 11 لاعبًا, 21 لاعبًا, 100 لاعب). Hebrew plurals were not checked yet.
- **Chinese, Cyrillic, accented Latin, the currency symbols and emoji draw** (see "Fonts").
- **The "update iOS" page** for browsers without WebAssembly GC is in place (not yet seen on a real old iPhone).

## Findings that change the plan

1. **Hebrew strings folder must be `values-he`, not `values-iw`.** Browsers report Hebrew as `he`, and Compose resources do not match it to `values-iw`: the page stayed English until the folder was renamed. Task 11 must rename it.
2. **Fonts are the real cost.** Without bundled fonts, Compose draws empty boxes for Hebrew, Arabic, Chinese and emoji until it downloads fonts from Google Fonts (`fonts.gstatic.com`) after the first screen. That also means:
   - letters are missing for a few seconds on every first load;
   - the app needs internet for them, so it wouldn't work offline or in places that block Google;
   - each visit sends a request to Google.
3. **Bundling fonts fixes it, but there is a catch:** fonts must be registered one at a time (`FontFamily(hebrew)` and `FontFamily(arabic)` in separate calls; given together, only one is used). Chinese needs a separate approach: the full CJK font is tens of MB, so it needs a subset with only the characters the translations use. Task 11 should do that.
4. **Strings appear a moment after the first screen** (they load asynchronously), so labels flash empty for a moment. Fixable by waiting for resources before showing the screen.
5. **Compose draws everything on a canvas.** The page has no real text in the page, so browser translation, find-in-page and some screen readers don't see it. Acceptable for a game app, but worth knowing.

6. **An amount placed after an English label in a right-to-left layout splits apart.** On the test page, `Current layout (RTL): 18.5 ₪` shows the `₪` at the far left and the `18.5` right after the label. This is not a Compose bug: plain HTML in the same browser draws it identically, because it is the standard Unicode bidirectional algorithm. With a Hebrew label (`קופה: 18.5 ₪`) it looks right, and wrapping the amount in an isolate keeps it together. In the app this only matters where a Latin-script name or label and an amount sit in one string inside a right-to-left screen, for example the shared text lines (`name ₪50`). Screens that draw the name and the amount as separate texts are not affected. Worth a check when the screens move over (task 13).

## Download size (measured from Firebase Hosting, Brotli)

| File | Size |
|---|---|
| Skia graphics engine (wasm) | 2.62 MB |
| App code (wasm) | 0.80 MB |
| App loader (js) | 0.08 MB |
| Hebrew font (bundled) | 0.06 MB |
| Arabic font (bundled) | 0.37 MB |
| **First load total** | **about 3.9 MB** |

Plus about 1 MB of fallback fonts from Google if emoji or Chinese appear. Later visits can come from the cache (task 18, PWA). The Arabic font is a variable font; a one-weight subset would be much smaller.

## Timing: not measurable here

The preview pane on this PC is hidden while I work, so the browser throttles it, and the first-screen times it gave (0.2 s to 14 s for the same page) are meaningless. **Real timings need your phone.** The page shows "First screen after: N ms" in its info box, and the browser tab title lists the font steps.

## iPhone checklist (about 10 minutes)

Open the link on an iPhone with iOS 18.2 or newer and, ideally, record the screen. Note the iOS version (Settings, General, About).

1. **Does it open?** You should see the test page, not the "update iOS" message.
2. **First load time**, stopwatch from tapping the link to seeing the page: on Wi-Fi, then on mobile data. Read "First screen after" at the bottom of the page. Then reload and time it again.
3. **Text fields**, in the Hebrew, Arabic, English and Mixed fields:
   - tap in the middle of a word: the caret should land there and not jump;
   - type Hebrew, then English, then digits;
   - delete quickly, hold the delete key;
   - select with a long press, copy and paste;
   - check autocorrect and the suggestion bar don't garble or duplicate text.
4. **Money field:** the keyboard should show digits and a decimal separator. Type `12,5`: it should become `12.5`. Check the formatted lines below the field.
5. **Keyboard behavior:** focus the lowest fields. Does the keyboard cover the field you're typing in? Does the page scroll to it? Does rotating the phone break anything?
6. **Right to left:** open `?lang=he` and `?lang=ar`. Does the layout mirror? Do the three direction buttons work?
7. **Letters:** check that Hebrew, Arabic, Chinese and the emoji show with no empty boxes, and note whether boxes appear for a moment first.
8. **Plurals:** `?lang=ru`, `?lang=ar` and `?lang=he` show the player counts.
9. **Install:** Share, Add to Home Screen. Open it from the icon: it should look and work the same, and the info box should say "Installed to home screen: true".
10. **Comparison links** (add to the address): `?hebrewonly` loads only the Hebrew font, `?nofonts` loads no fonts. Tell me which feels best.

Links, with the language forced:

- https://payup-ae6fc--spike-ac3nbvag.web.app/?lang=he
- https://payup-ae6fc--spike-ac3nbvag.web.app/?lang=ar
- https://payup-ae6fc--spike-ac3nbvag.web.app/?lang=ru
- https://payup-ae6fc--spike-ac3nbvag.web.app/?lang=zh-CN

## Go / no-go criteria (agreed)

- Typing Hebrew, Arabic and English on iPhone Safari works: no jumping caret, no broken deletion, selection and paste work.
- The decimal keyboard works for amounts, and an amount stays readable inside right-to-left text.
- Right-to-left layout mirrors correctly.
- No missing letters in Hebrew, Arabic or Chinese.
- Plurals come out right for Russian and Arabic.
- The first screen appears in under about 10 seconds on 4G, and the page works after Add to Home Screen.

A fail on typing or missing letters is a no-go.
