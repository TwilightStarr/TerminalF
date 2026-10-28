<p align="center"><img src="icon.svg" alt="TerminalF logo" width="112"></p>

<h1 align="center">TerminalF</h1>

<p align="center">English · <a href="README.tr.md">Türkçe</a></p>

A fast, minimal management and optimization app for Android. With root or Shizuku it applies system-level
tweaks in one tap, and lets you manage game performance, battery and storage from one place.

The app opens in **English**; switch to **Türkçe** any time under *Device > Notifications & Alerts > Language*
(or right on the welcome screen). The choice is remembered.

## Features

- **Home** – Live CPU, memory, temperature, battery and network status; battery-life estimate and alerts.
- **Optimize** – Ready-made tweaks: system animations, refresh rate, background restrictions, cache cleanup and more.
- **Apps** – List installed apps, freeze or remove them; system-critical packages are protected.
- **Game** – Per-game resolution scale and FPS target, ready-made profiles, a game watcher and thermal guard.
- **Device / Appearance / Settings** – Device info, 18 themes (default: AMOLED black), accent color, colorful UI,
  logo color (the home-screen icon follows it), notification and alert settings, language.

## Access

Root is tried first, otherwise Shizuku is used. Readiness is verified with a real command; a failed action is
never shown as successful. No data is sent over the network and there is no telemetry.

## Install & update

Download the APK from the [Releases](../../releases) page and install it. Later versions update **in place**,
without uninstalling; give this repository's link to any GitHub-based updater (e.g. Obtainium).

In-place updates rely on two things staying constant:

- **Same signature:** every release is signed with the same key. GitHub Actions generates the key on the first
  build and stores it on the `signing-keys` branch; later builds reuse it. Do not delete that branch.
- **Increasing version number:** `versionCode` grows automatically on each build (minute-based); no manual bump.

Do not change `applicationId` (`com.example.terminal_flutter`); Android would treat the app as a new one.

## Build

```
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # signed release APK
```

Requirements: JDK 17, Android SDK 35. Every push to `main` runs the tests, builds a signed APK and publishes a
new Release automatically (`.github/workflows/build.yml`). Put `[minor]` or `[major]` in the commit message to
bump the version accordingly.

No manual setup is needed for release builds. Optionally add your own key as the `KEYSTORE_BASE64` GitHub
secret; if defined it is used (don't change the key once chosen).

## Logo & icons

The logo is a near-black rounded tile with an "F" made of three bars fading from white to gray (`icon.svg`).
Regenerate the launcher assets with `python3 tools/gen_logo_assets.py` and `python3 tools/gen_launcher_icons.py`
(shared geometry in `tools/logo_common.py`; needs Pillow and numpy).

## Translations

Texts live next to the code as `tr("English", "Türkçe")` (see `core/I18n.kt`); the JSON files in
`app/src/main/assets/config/` carry optional `titleEn` / `descriptionEn` fields. To add a language, extend
`AppLanguage` and the `tr` helper.

## Requirements

minSdk 24, targetSdk 34.

## License

MIT, see [LICENSE](LICENSE).
