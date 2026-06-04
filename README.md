# Cobalt Android

An unofficial native Android client for [cobalt.tools](https://cobalt.tools) — the best way to save what you love, now with a share sheet, clipboard trigger, and a download queue that lives on your phone.

> **Not affiliated with or endorsed by the cobalt team.** This app wraps cobalt's public web interface and relies entirely on cobalt's service. All credit for the actual downloading magic goes to [imputnet/cobalt](https://github.com/imputnet/cobalt).

---

## What it does

- **Full-screen cobalt.tools WebView** — same UI you know, no browser chrome
- **Share from any app** — tap Share in YouTube, TikTok, Instagram, X, Reddit, etc. → select Cobalt → URL auto-fills and starts downloading
- **Clipboard trigger** — copy a link, open the app → snackbar: "download from clipboard?" — one tap
- **Long-press shortcuts** — "Paste & Download" and "Open Queue" from the home screen icon
- **Native download queue** — active progress, history, open/retry buttons; IBM Plex Mono throughout to match cobalt's aesthetic
- **Audio-only mode** — toggle in settings to pre-select cobalt's audio download mode
- **Configurable instance** — point the app at your own self-hosted cobalt instance
- **Works with local-processing** — cobalt's WASM FFmpeg merges run inside the WebView; blobs stream to your Downloads folder via a native bridge

Files save to **Downloads/Cobalt/** on your device.

---

## Install

This is a debug/sideload build. No Play Store listing yet.

1. Download `app-debug.apk` from [Releases](../../releases)
2. On your Android device: **Settings → Apps → Special app access → Install unknown apps** → enable for your file manager
3. Open the APK and tap Install
4. On first launch: grant notification permission and allow battery optimization exemption (keeps downloads running in the background)

Tested on **Samsung Galaxy S22 Ultra (Android 14 / One UI 6)**. Should work on any Android 8.0+ device.

---

## Backend

This app uses **cobalt.tools** directly — no self-hosted backend required. cobalt's public web interface handles all the media extraction.

If cobalt.tools is unavailable or you want to run your own instance, tap **⋯ → cobalt instance** to enter any cobalt API URL. See [cobalt's self-hosting docs](https://github.com/imputnet/cobalt/blob/main/docs/run-an-instance.md) to spin one up.

> Per cobalt's own documentation: the hosted API at `api.cobalt.tools` is not intended for automated use. This app targets cobalt's web interface (cobalt.tools), not the raw API, so it respects that boundary.

---

## Build from source

Requirements: Android Studio or Android SDK (API 26–35), Java 17, internet access for first Gradle sync.

```bash
git clone https://github.com/Andro-Meta/cobalt-android
cd cobalt-android
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Tech stack

- Kotlin · Jetpack (Room, ViewModel, LiveData, WorkManager)
- OkHttp for download streaming → MediaStore
- Material Design 3 · IBM Plex Mono (matching cobalt's design language)
- AGP 8.5.2 · Gradle 8.7 · minSdk 26 · targetSdk 35

---

## Known limitations

- **No signed/Play Store release** — sideload only for now
- **Local-processing (WASM FFmpeg) can be slow** on large files; this runs client-side inside the WebView
- **Retry on reconnect** shows a notification — you tap to re-trigger rather than silently resuming (cobalt tunnel URLs expire, so a full re-submit is needed)

---

## Credits

Built on top of [cobalt](https://github.com/imputnet/cobalt) by [imputnet](https://github.com/imputnet) and contributors — the actual media downloading is entirely their work. This app just puts it in your share sheet.
