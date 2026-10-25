# Prism Client

[![Android release workflow](https://github.com/ayush04-op/PrismClient/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/ayush04-op/PrismClient/actions/workflows/android.yml) [![Latest release](https://img.shields.io/github/v/release/ayush04-op/PrismClient?label=latest%20APK)](https://github.com/ayush04-op/PrismClient/releases/latest)

Prism is an unofficial, native Android Telegram client with its own visual identity and customization. It is built from Telegram for Android and incorporates work from Inugram and entinyGram, alongside Prism-specific changes. Prism is not affiliated with or endorsed by Telegram.

## Get Prism

The latest published release is **build 35**, a signed, arm64, pluginless APK for Android 8.0 (API 26) and newer.

[Download the latest APK](https://github.com/ayush04-op/PrismClient/releases/latest) · [View build 35 release notes](https://github.com/ayush04-op/PrismClient/releases/tag/build-35)

The build-35 APK is [`prism-arm64.apk`](https://github.com/ayush04-op/PrismClient/releases/download/build-35/prism-arm64.apk). Its SHA-256 digest is `6f5e6928bb6288b81e10aaba3cd426d8bf97625a8e1eca1f0118d3e9223574d5`. Install it over a previous Prism build signed with the same key to update.

## What’s in build 35

- **Paper themes:** Paper and Paper Dark with coordinated color palettes. Other built-in themes are hidden, while themes you import or create remain available. Chat themes are off by default.
- **Typography:** Prism Serif is the default app font, Prism Sans is also available, and TTF/OTF font families can be imported. Anthropic Mono is used for monospaced text and code.
- **Prism branding:** custom adaptive launcher icons use Prism’s own mark across the themed icon set.
- **Interface customization:** adjustable Liquid Glass effects, chat-header controls, and message-input previews.
- **Settings and updates:** Prism and Telegram settings are grouped together, with a dedicated in-app software-update page.
- **Telegram client features:** privacy, messaging, media, and other inherited capabilities from the upstream projects. See the [feature inventory](FEATURES.md) and [credits](CREDITS.md); some inherited or plugin-only options may not appear in the pluginless release.

## Build from source

The project uses the Telegram Android source and a patch-based build setup. For local setup, Windows-specific instructions, and Gradle commands, see [BUILD.md](BUILD.md) and [WINDOWS_SETUP.md](WINDOWS_SETUP.md).

The GitHub Actions workflow is [`Build Prism APK`](.github/workflows/android.yml). It builds the pluginless arm64 release when manually dispatched or when code is pushed to `main`. The workflow requires the repository secrets `API_ID` and `API_HASH`. `KEYSTORE_PASSPHRASE` is optional; when configured, CI signs the APK and publishes a GitHub Release.

## Source and licensing

Prism’s own additions are licensed under MIT; see [LICENSE](LICENSE). The combined Android app also includes Telegram for Android, licensed under GPL-2.0-or-later, and other upstream components with their own licenses. See [LICENSES](LICENSES) and [CREDITS.md](CREDITS.md) for attribution and licensing details. This repository contains Prism’s source overlay, patch stack, and build instructions; setup fetches the upstream Telegram source needed to reconstruct the build.
