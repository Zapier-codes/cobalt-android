# cobalt-android — Product & Architecture Spec

## Vision
A Vidmate-style media companion app, upgraded with a YouTube-caliber navigation
model and a TikTok-caliber Shorts feed. Fully usable standalone (NewPipe-level
extraction, no external dependency), with an optional Cobalt-powered upgrade
unlocked via user-supplied API key. All visual identity — theme, colors, app
name/branding — is driven remotely by a config dashboard, so changes propagate
without a rebuild. Everything else about the app runs entirely on-device.

## 0. No Accounts, No Login — Anywhere
- The app must never require sign-up, login, or any account creation, for any
  feature, at any point.
- Anything that would normally be tied to a user account (watch history, likes,
  "for you" preference weighting, downloads list) is stored **locally on-device
  only** (Room), not synced to any backend or identity.
- If a feature would only make sense with an account (comments tied to identity,
  cross-device sync, subscriptions to creators), either omit it or implement a
  local-only equivalent — never gate a screen or action behind login.

## Core Tech Stack (target state)
- Kotlin, Jetpack Compose (migrating off View-based UI)
- Clean Architecture: presentation / domain / data layers, clear module boundaries
- Hilt for DI
- Coroutines + Flow for async/state
- Room (local persistence + local resolution cache, see section 10), WorkManager
  (background jobs, incl. downloads), OkHttp (networking)
- Min SDK 26 / Target SDK 35

## 1. Navigation Structure

### Bottom navigation (3 tabs)
- **Home** — Vidmate-style trending/discovery page
- **Shorts** — full-screen vertical short-form feed (default landing tab)
- **Settings** — app settings, Cobalt key entry

### Top navigation (persistent on Home)
- Search bar (with paste-URL recognition — see section 5)
- Downloads icon (shortcut to downloads/library — see section 6)

### Default landing page = Shorts
- Opens directly into the Shorts feed on app launch, not Home.

## 2. Shorts Page (TikTok-style vertical feed)
- Full-screen 9:16 vertical video feed, one video per screen.
- **Autoplay**: current video plays automatically on view; mute state persists
  across videos.
- **Sequential advance, not preloaded batch**: scrolling advances to the *next*
  single video, which then autoplays — mirrors TikTok's one-at-a-time paging.
  Previous video pauses/releases when swiped away.
- **Preload strategy**: only the *next single* video pre-buffers while the
  current one plays — not a batch. Combined with the local resolution cache
  (section 10), this keeps bandwidth and redundant resolution calls bounded.
- **On-screen controls/overlay**:
  - Tap to pause/resume
  - Mute/unmute toggle
  - Video info overlay: title/channel/description (truncated, expandable)
  - Right-side action rail: like (local-only), share, download, no comment/
    account-gated actions
  - Thin progress indicator for current video position
- Reference: **InnerTube** sources Shorts-equivalent content, consistent with
  the standalone extraction approach in section 4.
- Vertical pager (Compose `Pager` with snap) with page-by-page playback
  lifecycle — each page's ExoPlayer starts on becoming active, releases on
  becoming inactive.

## 3. Home Page (Vidmate-style discovery)
- Trending/recommended items surfaced based on:
  - **Local preferences** — derived on-device from local watch/like history
    (Room), no server-side account profile.
  - **User location** — content reflects what's trending/relevant locally.
- Layout: horizontal category rows (e.g. "Trending near you," "Popular") plus
  a browsable grid.
- Each card: thumbnail, title, source/channel, duration badge. Tap opens an
  overlay detail/player view (section 7).
- Uses skeleton placeholders while loading (section 11) and the local
  resolution cache (section 10) so returning users see instant content.

## 4. Extraction Engine — Dual Mode
### Standalone mode (default, no Cobalt key required)
- NewPipe-Extractor (or equivalent) as the core extraction library.
- InnerTube used for Shorts-equivalent content sourcing.
- Full functionality without any external service dependency.

### Cobalt-enhanced mode (optional upgrade)
- Settings screen: user pastes a Cobalt API key (locally stored, no account).
- Once validated, routes extraction through Cobalt where it offers higher-
  quality sources / broader platform support, falling back to NewPipe-
  Extractor/InnerTube where Cobalt doesn't cover a source.
- Additive layer — standalone mode always keeps working without a key.
- Note: Cobalt itself is an external API the user opts into by supplying a
  key — this is the one piece that isn't purely on-device by nature, since
  it's a third-party service the user explicitly chooses to use. Everything
  else in the app (caching, storage, preferences, standalone extraction)
  stays on-device regardless of whether Cobalt mode is active.

## 5. Smart Search Bar
- Top nav, visible on Home.
- Paste-URL recognition: detects a pasted media URL and auto-routes to fetch/
  preview instead of a text query; falls back to normal search otherwise.

## 6. Downloads
- **Universal link downloader**: users can paste *any* supported link — not
  only items already surfaced in-app — and download it. Same resolution/
  extraction pipeline (section 4) handles both in-app items and pasted links;
  there is no separate code path for "external" vs "internal" downloads.
- **Format & quality selection**: for every downloadable item, user picks:
  - **Video** (multiple qualities/resolutions, whatever the source exposes —
    e.g. 360p/720p/1080p/best-available) or
  - **Audio-only** extraction (e.g. m4a/mp3), for music/audio-focused downloads
  - Mirrors Vidmate's format/quality picker pattern.
- Downloads run via WorkManager in the background; support pause/resume/retry
  and concurrent download limits (see enhancement in section 12).
- Downloads icon in top nav opens a local downloads list (no account tie-in);
  files stored locally, fully playable offline.
- Available as an action from Home, Shorts, and the universal-link entry point.

## 7. Overlay Navigation Model
- Secondary routes (video detail, full player, settings sub-pages, downloads
  list) render as overlays on top of the current tab, not full screen
  transitions.
- Underlying routing/back-stack works normally (deep links, back button,
  process death/restoration) — overlay presentation is a UI layer on top of
  standard navigation.
- Goal: one continuous app surface, YouTube/TikTok-caliber smoothness.

## 8. Settings
- Cobalt API key entry + validation status.
- Remote config connection status (debug builds).
- Location permission control for Home personalization, with fallback
  (section 9).
- Clear local data option (wipes local history/likes/preferences/cache).
- Standard app settings (default download quality/format, storage location).

## 9. Permissions & Fallbacks
- **Location**: requested for Home personalization only, never required to use
  the app. If denied, fall back to a manual region/country picker so the user
  still gets locally-relevant content without granting the permission.
- No other runtime permission gates any core feature.

## 10. Local Caching & Resolution Layer (fully on-device, no backend)
Everything in this app runs entirely on the user's device — there is no
server, backend, or shared service of any kind. Caching is scoped per-device
only: it makes *this user's* repeat visits fast. It does not and cannot serve
other users' requests, since that would require a shared server, which is
explicitly out of scope for this app.

- **Local cache (Room)**: resolved stream metadata/URLs and extracted info are
  cached on-device with a TTL. Reopening a previously-viewed item reads from
  local cache first; only re-resolves on cache miss or expiry.
- **Cache-first DAO pattern**: DAOs check local cache → serve immediately if
  fresh → refresh in the background if stale, rather than blocking on a fresh
  resolve every time a screen opens. This is what prevents the same device
  from re-resolving the same item on every visit.
- **Cache invalidation**: TTL-based (short for trending/fast-changing content,
  longer for stable metadata like titles/thumbnails); manual invalidation path
  for when a resolved stream URL expires and playback fails.
- **Prefetch on Home/Shorts load**: as items are fetched for the visible feed,
  cache them immediately so scrolling back or reopening within the session is
  instant — still entirely local, no cross-device sharing.

## 11. Skeleton Loading
- Every content-loading surface (Home rows/grid, Shorts feed entries, search
  results, downloads list) shows skeleton/shimmer placeholders matching the
  final layout shape while data loads — never a blank screen or a bare spinner.
- Skeletons resolve to real content progressively as items arrive, rather than
  waiting for the entire batch before rendering anything.

## 12. Suggested Enhancements
Three additions worth building in, beyond what's specified above:

1. **Background / mini-player audio mode** — when the app is backgrounded or
   the screen locked, video continues as audio-only playback (or a small
   picture-in-picture window), so users don't lose playback when they switch
   apps. Common in Vidmate-class apps and materially improves perceived
   quality.
2. **Network-aware adaptive quality** — auto-select playback (and suggested
   download) quality based on current connection speed/type (Wi-Fi vs mobile
   data), with a manual override in Settings. Reduces buffering on Shorts
   specifically, where feel matters most.
3. **Download queue manager** — a proper queue (not fire-and-forget) for
   downloads: concurrent-download limit, per-item progress, pause/resume/
   retry/cancel, and automatic retry with backoff on transient failures.
   Pairs directly with section 6's universal downloader so multiple pasted
   links or in-app downloads don't compete uncontrolled for bandwidth.

## Non-Goals (for now)
- No user accounts / cloud sync, in this phase or any future phase implied by
  this doc — see section 0.
- No monetization/paywall logic in this phase.
- No comments or social features requiring identity.
- No backend, server, or shared service of any kind — everything runs
  on-device (Cobalt, if the user opts in, is the sole external API call,
  and is explicitly user-initiated via a locally-stored key).

## Build Sequencing (for the agent)
Work in this order, each phase landing and passing CI before the next begins:
1. Bottom-nav shell (Home / Shorts / Settings) + top-nav search bar/downloads
   icon + dynamic theming scaffold + local Room schema for history/likes/
   preferences/resolution-cache (sections 0, 10) + skeleton loading components
   (section 11) reused across every screen from the start
2. Shorts feed: InnerTube-sourced content, vertical pager, autoplay + sequential
   playback + single-video-ahead preload + action rail — default landing
   experience, prioritize feel
3. Home page: NewPipe-Extractor integration, trending rows + grid, location +
   local-preference-based content, cache-first DAO reads, permission fallback
4. Settings + Cobalt key entry + dual-mode extraction routing + clear-local-data
5. Downloads: universal link downloader, format/quality picker, WorkManager
   queue manager (enhancement 3), offline playback
6. Smart search bar paste-URL recognition
7. Enhancements: background/mini-player audio mode, network-aware adaptive
   quality
8. Overlay navigation polish pass: transitions, remote config live-refresh,
   edge cases

Each phase should be a small, reviewable set of commits — not one giant rewrite commit.
