# Bundled fonts

The web app ships its own fonts so that no letters show as empty boxes and nothing is requested from Google. They are
Noto fonts under the SIL Open Font License (the licence texts are in this folder). Except for Hebrew and Arabic (kept
whole, from the Spike A work), they are cut down with fontTools to what the app draws:

| File | Source | What is kept |
|---|---|---|
| `noto_sans_hebrew.ttf`, `noto_sans_arabic.ttf` | notofonts (Hebrew, Arabic) | everything |
| `noto_sans_sc.ttf` | google/fonts `ofl/notosanssc` | regular weight only; the characters of the Chinese translations. Bold is synthesized. Chinese player names that use other characters fall back to the browser |
| `noto_color_emoji.ttf` | google/fonts `ofl/notocoloremoji` | the 8 emoji the app draws (crown, trophy, medals, coin, handshake, rain cloud); the SVG table is dropped |
| `noto_sans_symbols2.ttf` | google/fonts `ofl/notosanssymbols2` | the card suits, the arrow and the minus sign |

When a screen starts drawing a new emoji or Chinese characters, re-cut the font to include them.
