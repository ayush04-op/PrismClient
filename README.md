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

## License
MIT for Prism's own code (see `LICENSE`). The Telegram base code is GPL-2.0-or-later,
so any distributed build must be accompanied by this source code.

## Customising (Prism overlay)
Everything Prism adds on top of the base patch stack lives in `prism/` and `src/fork/prism/`:
- `prism/patches/*.patch`: small edits to Telegram's own files (intro, login, lite-mode defaults)
- `prism/res/`: logo, launcher icon
- `prism/assets/prism_font/`: drop a `.ttf`/`.otf` here to make it the default font
- `src/fork/prism/`: the welcome screen, aurora background and glass components
