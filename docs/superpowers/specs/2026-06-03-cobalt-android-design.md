# Cobalt Android — Design Spec
**Date:** 2026-06-03
**Status:** Approved

## Overview
A native Android app (Kotlin) that wraps cobalt.tools in a full-screen WebView with share sheet
integration, clipboard detection, a native download queue, and connectivity-aware retry. Zero
backend infrastructure required — the app uses cobalt.tools directly.

Target device: Samsung Galaxy S22 Ultra (Android 14 / One UI 6.x).
Min SDK: 26 (Android 8.0). Target SDK: 35 (Android 15).

---

## Architecture

Six components, each with a single responsibility:

| Component | Role |
|---|---|
| `MainActivity` | Hosts WebView; handles share intents, shortcuts, clipboard check on resume |
| `CobaltWebView` | WebView subclass — JS injection, DownloadListener, JS bridge |
| `DownloadService` | Foreground service (dataSync); OkHttp streaming → MediaStore; chunked blob |
| `DownloadDatabase` | Room DB — DownloadRecord (id, originalUrl, filename, mimeType, bytesDownloaded, totalBytes, status, timestamp) |
| `DownloadQueueSheet` | Material bottom sheet — active/history tabs, progress, open/share/retry |
| `NotificationHelper` | Per-download progress notifications with Open/Share/Cancel/Retry actions |

**Tech stack:** Kotlin · Jetpack (Room, ViewModel, LiveData, WorkManager) · Material 3 · OkHttp
**Font:** IBM Plex Mono (bundled — matches cobalt.tools exactly)

---

## Visual Design

Mirrors cobalt.tools' design tokens:

```
Dark theme (default):
  background:      #000000
  surface:         #191919
  surface-sidebar: #131313
  surface-elevated:#282828
  text-primary:    #e1e1e1
  text-secondary:  #818181
  accent-blue:     #2a7ce1
  success-green:   #37aa42
  error-red:       #ed2236
  stroke:          rgba(255,255,255,0.05)
  input-border:    #383838
  border-radius:   11dp
  font:            IBM Plex Mono
```

**Main screen:** edge-to-edge WebView, no app bar. Floating queue FAB (42dp, bottom-right,
`#191919` fill, blue badge for active download count).

**Clipboard snackbar:** `#191919` pill at bottom — `"download from clipboard?"` — tap to confirm,
auto-dismiss after 5s.

**Download Queue sheet:** `#131313` background, draggable handle. Active/history tabs.
Each row: filename (IBM Plex Mono, `#e1e1e1`), service + size + status (`#818181`),
2dp progress bar (`#383838` track → `#2a7ce1` fill), pill action buttons.

**Settings overlay:** `⋯` native button floated over WebView (not injected into DOM).
Sections: cobalt instance URL, audio-only toggle, clipboard trigger toggle,
battery optimization, clear history.

**App shortcuts (long-press icon):**
- "Paste & Download" — reads clipboard, opens app, pre-fills URL
- "Open Queue" — opens app to download queue

---

## Data Flows

### A — URL → cobalt input
1. URL arrives via share intent, clipboard confirm, or shortcut
2. If cobalt.tools loaded: inject JS — set input value, dispatch `InputEvent{bubbles:true}`,
   if audio-only ON inject settings toggle first, then click submit
3. If not loaded: load page, inject after `onPageFinished()`
4. Fetch-wrapper JS (injected on every load) intercepts the outbound cobalt API call
   and calls `CobaltBridge.onUrlSubmitted(originalUrl)` — stored in DownloadRecord for retries
5. Svelte binding note: use native input value setter trick + dispatch input event
6. All injection wrapped in try/catch; fallback: navigate with URL as `#hash`

### B — Download interception
1. cobalt.tools JS triggers `<a download>` or Blob URL click
2. `DownloadListener.onDownloadStart()` fires with url, mimeType, contentDisposition, length
3. **https:// URL:** DownloadService fetches via OkHttp using WebView cookies +
   User-Agent header, streams to `MediaStore Downloads/Cobalt/`
4. **blob: URL:** JS bridge reads blob as ArrayBuffer, sends in 2MB chunks to native bridge,
   DownloadService reassembles into MediaStore

### C — Progress → UI
- DownloadService updates Room every 500ms (bytesDownloaded, status)
- Room LiveData → DownloadQueueSheet ViewModel → UI redraw
- NotificationHelper posts progress notification
- On COMPLETE: `"filename saved"` notification with Open/Share
- On FAIL (network): WorkManager RetryDownloadWorker (constraint: CONNECTED,
  payload: originalUrl, exponential backoff 30s→2m→8m→30m, max 3 attempts)
- On retry wake: notification `"tap to retry [title]"` → MainActivity → Flow A restart

### D — Picker (Instagram/Twitter multi-item)
cobalt.tools renders picker UI in WebView. Each user selection fires `onDownloadStart()`.
Queue sheet groups by originalUrl into one expandable item.

### E — local-processing (WASM FFmpeg)
cobalt.tools runs WASM FFmpeg inside WebView for high-res YouTube merges.
Output is a Blob URL — handled by blob branch of Flow B.
Toast shown: `"merging locally — may take a moment"`.

---

## Error Handling

| Error | Owner | User sees |
|---|---|---|
| cobalt.tools unreachable | `WebViewClient.onReceivedError` | Cobalt-styled dark error screen + retry button |
| cobalt API error | cobalt.tools (rendered in WebView) | cobalt's own error UI |
| Download fails — network drop | DownloadService | Queue row red + [retry]; WorkManager auto-retry |
| Download fails — server error | DownloadService | Queue row red + [retry]; no auto-retry |
| Blob chunk OOM/bridge error | JS bridge + DownloadService | Toast `"local merge failed"` + [retry] in queue |
| Storage full | DownloadService (IOException) | Persistent notification: `"not enough storage"` |
| Notification permission denied | First-launch flow | Silent degradation — queue sheet still works |
| Battery optimization active | First-launch prompt | One-time dialog → system battery settings |
| Bad custom cobalt URL | WebViewClient.onReceivedError | Error screen + `"check your cobalt instance URL in settings"` |
| Non-URL share intent | MainActivity | Snackbar: `"that doesn't look like a supported link"` |

**Retry budget:** 3 attempts, exponential backoff. After 3 failures: manual [retry] only.
**No crash dialogs:** all DownloadService exceptions caught at service level → FAILED status.

---

## Android 14 Compatibility Notes

- MediaStore API required for writing to Downloads (not raw file paths)
- `foregroundServiceType: dataSync` required in manifest for Android 14+
- Runtime notification permission (`POST_NOTIFICATIONS`) requested on first launch
- Background clipboard access blocked — clipboard checked only on `onResume()`
- Battery optimization whitelist prompt on first launch (One UI aggressive doze)
- `network_security_config.xml` to allow cleartext HTTP for cobalt tunnel URLs

---

## Testing Plan

### Unit tests (JVM)
- URL domain matching (positive: youtube.com, youtu.be, tiktok.com, x.com, instagram.com,
  reddit.com, vimeo.com, soundcloud.com; negative: random text, mailto:, non-URLs)
- DownloadRecord state machine (valid/invalid transitions)
- Blob chunk reassembly (N × 2MB arrays → correct concatenation, partial failure → no partial file)
- WorkManager retry backoff (3-attempt cap, exponential delays)
- Settings persistence (Room in-memory)

### Instrumented tests (emulator)
- WebView loads cobalt.tools (IdlingResource on onPageFinished)
- JS injection fills input field
- Share intent pre-fills input
- Clipboard snackbar appears for supported URLs, hidden for non-URLs
- DownloadQueueSheet renders DOWNLOADING/COMPLETE/FAILED rows correctly
- Settings screen persists across rotation

### Manual smoke tests (S22 Ultra)
1. Share YouTube URL → cobalt pre-fills → downloads to Downloads/Cobalt/
2. Copy TikTok URL, switch to app → snackbar → tap → downloads
3. Long-press icon → "Paste & Download" → pre-filled and downloads
4. Instagram carousel → picker UI → select 2 → both in queue
5. Airplane mode mid-download → row red → disable → retry notification → completes
6. Audio-only ON → share YouTube URL → .mp3 downloaded
7. Full storage → "not enough storage" notification
8. Bad cobalt URL in settings → cobalt-styled error + hint
9. Deny notifications → downloads complete → queue sheet shows results
