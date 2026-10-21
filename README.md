# Prism

A fast, native Telegram client for Android with a Liquid Glass look.

Prism is built on the work of others — see [CREDITS.md](CREDITS.md). It is **not** an official Telegram app.

## Status
Early work in progress. Step 1 is a clean, working build; the glass UI comes next.

## Build
The APK is built by GitHub Actions (`Actions` tab → `Build Prism APK` → Run workflow).
Add these repository secrets first (Settings → Secrets and variables → Actions):
- `API_ID`, `API_HASH`: your own keys from https://my.telegram.org

Download the APK from the finished run's **Artifacts**.

## Open source
Prism is free and open source. Source: https://github.com/ayush04-op/PrismClient

- Prism's own additions are under the MIT license (`LICENSE`).
- The app is built on Telegram for Android, which is **GPL-2.0-or-later** (`LICENSES/Telegram-GPL-2.0.txt`),
  so the combined app is distributed under the GPL and its full source is this repository.
- Built on entinyGram (MIT) and Inugram; see [CREDITS.md](CREDITS.md).
- The default font (Anthropic Serif Text) is a third-party font with its own terms; see `prism/assets/prism_font/FONT-NOTICE.txt`.

Prism is an unofficial client and is not affiliated with Telegram.

## Customising (Prism overlay)
Everything Prism adds on top of the base patch stack lives in `prism/` and `src/fork/prism/`:
- `prism/patches/*.patch`: small edits to Telegram's own files (intro, login, lite-mode defaults)
- `prism/res/`: logo, launcher icon
- `prism/assets/prism_font/`: drop a `.ttf`/`.otf` here to make it the default font
- `src/fork/prism/`: the welcome screen, aurora background and glass components
