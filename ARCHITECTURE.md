# Cobalt-Android — Full Architecture & Build Sequencing

This document is the single source of truth for what Hermes builds, in what
order, and how it knows a phase is actually done. It replaces the previous
architecture description. Every phase below has an explicit file list and a
**Definition of Done** — Hermes should never have to guess what "the next
concrete step" is.

## No stubs, no placeholders — full working implementations only

Every file, function, and screen produced under this spec must be a real,
functioning implementation — not a stub, not a `TODO`, not a mock/fake data
source left in permanently, not a "wire this up later" comment. Specifically:

- If a phase requires a network call, write the real network call (using
  whatever HTTP client is already in the project, or Retrofit/OkHttp if
  none exists yet) — not a hardcoded fake response left in place as the
  final state. A short-lived mock is only acceptable mid-cycle if the very
  same cycle replaces it with the real call before committing.
- If a phase requires a database write, it must actually persist and be
  queryable — not an in-memory list standing in for Room.
- If a phase requires a UI interaction (tap, swipe, submit), the listener
  must perform the real action end-to-end, not log/toast a placeholder
  message.
- "Definition of Done" below means done — every condition must be
  genuinely true in the committed code, not approximately true or true
  "in spirit." A phase is not complete if any of its files contain a
  `TODO`, `FIXME`, `// implement later`, empty function body, or a
  hardcoded/fake value standing in for real logic.
- If a single cycle's bounded unit of work can't reach a fully working
  state on its own (e.g. it depends on a file from an earlier, unfinished
  step), do less scope that cycle rather than write a stub — pick a
  smaller piece of the phase that CAN be fully implemented in one cycle,
  instead of writing a partial/fake version of the larger piece.

## Unified enhancement, NOT a parallel rebuild

This is the single most important rule in this document, added after this
session found the previous version of this spec had Hermes about to build a
second, competing download system next to one that already works.
**`cobalt-android` is a mature, already-functioning app.** Every phase below
must **extend and connect to what already exists**, never re-invent it under
a new name. Before creating ANY new file, grep the existing codebase for
something that already does the job — if it exists, wire into it instead of
writing a parallel version.

**Already exists — reuse these, do not recreate them:**
- `download/DownloadRecord.kt`, `DownloadDao.kt`, `DownloadDatabase.kt`,
  `DownloadRepository.kt` — the real Room-backed download schema. Fields:
  `originalUrl`, `cobaltUrl`, `filename`, `mimeType`, `status`
  (`DownloadStatus` enum: QUEUED/DOWNLOADING/FAILED_NETWORK/FAILED/COMPLETE),
  `bytesDownloaded`/`totalBytes`, `mediaStoreUriString`, `retryCount`.
  **Do not create `DownloadEntity` — `DownloadRecord` already is that
  entity.**
- `download/DownloadService.kt` — a real foreground `Service` with
  `ACTION_HTTPS` (direct URL → OkHttp download → MediaStore, with live
  progress) and `ACTION_BLOB` (now unused post-WebView-removal, safe to
  leave dormant or remove in a later phase, not this one). Call
  `DownloadService.startHttps(...)` to enqueue a real download.
  **Do not create a new `DownloadWorker` for the download itself** —
  `DownloadService` already does this end-to-end.
- `download/RetryDownloadWorker.kt` — WorkManager-based retry-on-network-fail,
  already wired from `DownloadService.handleNetworkFail()`.
- `util/NotificationHelper.kt` — already produces the foreground/progress/
  complete/failed notifications `DownloadService` uses. **Do not create
  `DownloadNotificationManager` — this is that manager.**
- `download/MediaStoreWriter.kt` — already handles writing completed files
  into shared storage.
- `ui/HomeFragment.kt`, `ui/ShortsFragment.kt`, `ui/SettingsFragment.kt`,
  `ui/shorts/ShortsViewModel.kt`, `ui/shorts/ShortsAdapter.kt` — already
  exist and are wired into `nav_graph.xml` / the bottom nav.
- `util/SettingsRepository.kt` — already persists `cobaltInstanceUrl` and
  other settings; extend this file for new preferences rather than adding a
  parallel settings store, unless a phase explicitly calls for Room-backed
  preferences (Phase 7 — and even then, check this file first).

**Genuinely new in this rewrite (nothing existing covers these):**
- `ResolutionCacheEntity` + DAO (Phase 4) — no existing equivalent.
- `ResolutionPickerDialog` (Phase 4) — no existing equivalent.
- A repository that calls the cobalt API directly to resolve a pasted link
  into a real media URL (Phase 3) — replaces what WebView used to do.
- Everything in Phases 2, 5, 6, 7 that has no existing file (Shorts caching,
  downloads library screen, history/likes, settings screen content).

## No more WebView

As of this session, `CobaltWebView.kt` and `CobaltJsBridge.kt` are removed,
and `MainActivity.kt` no longer implements `CobaltWebView.Listener` or
references `binding.webView` (the layout has not had a `WebView` since the
bottom-nav rewrite in an earlier session — `MainActivity.kt` had dead code
still referencing it, which would not compile; this is fixed). All link
resolution and download-triggering must go through direct API calls
(OkHttp, already a dependency) feeding into the existing `DownloadService`
above — never by loading a page in a `WebView` and scraping/bridging out of
it. Phase 3's `LinkResolverRepository` is the one place a real HTTP call to
the cobalt instance API belongs.

Design references (patterns only, no code or assets copied):
- **Velune** (open-source YouTube Music client) — for layered MVVM/Clean
  Architecture structure: `ui/screens`, `viewmodels`, `repository`,
  `db/entities` + `db/daos`, `di`, and a WorkManager-based download-manager
  module with its own notification manager.
- **YouTube / TikTok home & shorts feed conventions** — vertical full-screen
  video feed with a bottom nav bar, per-item engagement affordances, and a
  separate top-level Home/Discovery surface distinct from the feed itself.
- **Vidmate-style downloader UX** — paste-a-link (or in-app browse) entry
  point, format/resolution picker before download, a persistent download
  queue with progress, and a downloaded-files library organized for offline
  playback.

## Tech stack (unchanged from existing repo)
- Kotlin, View-based UI (existing `activity_main.xml` / `MainActivity.kt`
  approach — NOT Compose, to match what's already in the repo)
- **No WebView** — removed this session. Link resolution is a direct API
  call (OkHttp, already a dependency), not a WebView/JS-bridge.
- Room for local persistence — `DownloadRecord`/`DownloadDao` already exist,
  see "Unified enhancement" above.
- Material Components (already a dependency per `build.gradle.kts`)
- WorkManager for background downloads — `RetryDownloadWorker` already
  exists; only genuinely new WorkManager usage should be added if a phase
  needs something the existing `DownloadService` doesn't cover.
- Standard Android `ViewModel` + `LiveData`/`Flow`

## Package structure (target — build toward this, don't require it before
   phase 1 is done)
```
app/src/main/java/com/cobalt/android/
├── ui/
│   ├── home/            HomeFragment, HomeViewModel
│   ├── shorts/          ShortsFragment, ShortsViewModel, ShortsAdapter
│   ├── downloads/        DownloadsLibraryFragment, DownloadsViewModel
│   ├── history/         HistoryFragment, HistoryViewModel
│   └── settings/        SettingsFragment, SettingsViewModel
├── shorts/               NEW in Phase 2 — the merged-feed data layer, kept
│   ├── model/            separate from ui/shorts/ (which is presentation-only)
│   ├── source/           InnertubeShortsSource, NewPipeShortsSource,
│   │                     InvidiousShortsSource, ShortsQueryFeeder, and the
│   │                     NewPipeExtractor OkHttp/init glue
│   ├── db/               ShortsDatabase, ShortsCacheEntity/Dao — separate
│   │                     from download/DownloadDatabase.kt on purpose, see
│   │                     Phase 2 below
│   └── ShortsFeedRepository.kt
├── db/
│   ├── entities/         one file per entity (see per-phase lists below)
│   ├── daos/             one DAO interface per entity group
│   └── CobaltDatabase.kt Room database, version-controlled with migrations
├── repository/           one repository per feature area, wraps DAO + network
├── download/             ALREADY EXISTS — DownloadService, DownloadRecord,
│                         DownloadDao, DownloadDatabase, DownloadRepository,
│                         RetryDownloadWorker. Extend, do not duplicate.
├── di/                   manual DI or Hilt modules (match whatever the repo already uses)
└── MainActivity.kt       hosts bottom nav + fragment container (already exists)
```

## Build Sequencing — 20 phases, in strict order

**Restructured from 8 phases to 20 in Session 5.** The original 8-phase plan
bundled too much real work into single phases (e.g. old Phase 5 was "build
the entire Downloads library screen, live progress, AND tap-to-play" as one
unit) — in practice that meant a single Hermes session often couldn't finish
a phase's Definition of Done in one cycle, leaving `state.json` and the real
repo disagreeing about how far along a phase actually was. Each of the 20
phases below is scoped to be finishable end-to-end (real code, no stubs) in
one session's worth of cycles. No phase's *requirements* were dropped in the
split — old Phase 5, for example, is now Phases 7+8; every file and
Definition-of-Done bullet from the 8-phase version still exists somewhere
below.

Hermes must not start phase N+1 until phase N's Definition of Done is fully
met and committed. If ARCHITECTURE.md and state.json disagree about which
phase is current, ARCHITECTURE.md's Definition of Done checks (verified by
inspecting the actual repo) are the source of truth, not state.json's
`current_phase` field.

---

### Phase 1 — Bottom-nav shell + theming scaffold
**Files:**
- `app/src/main/res/layout/activity_main.xml` — CoordinatorLayout with a
  `BottomNavigationView` (3 items: Home, Shorts, Settings) and a
  `FrameLayout` fragment container
- `app/src/main/res/menu/bottom_nav_menu.xml`
- `app/src/main/res/drawable/ic_home.xml`, `ic_shorts.xml`, `ic_settings.xml`
- `app/src/main/java/com/cobalt/android/MainActivity.kt` — wires
  `BottomNavigationView.setOnItemSelectedListener` to swap fragments
- `app/src/main/res/values/themes.xml` — Material You dynamic color support
  (`DynamicColors.applyToActivityIfAvailable`)
- `app/src/main/res/values/strings.xml` — nav labels

**Definition of Done:**
1. `activity_main.xml` contains a `BottomNavigationView` with exactly 3 menu
   items referencing `bottom_nav_menu.xml`. ✅ done.
2. `MainActivity.kt` wires the bottom nav to actually swap the fragment
   container's content. The existing implementation uses
   `BottomNavigationView.setupWithNavController(navController)` against
   `nav_graph.xml` — this is the Navigation-Component equivalent of a manual
   `onItemSelectedListener` and satisfies this requirement as-is. ✅ done.
3. All three destination fragments exist as real files and are declared in
   `nav_graph.xml` (`HomeFragment`, `ShortsFragment`, `SettingsFragment`).
   ✅ done.
4. `bg_fab.xml` is untouched (oval FAB background, not an icon). ✅ verified
   unchanged since its original commit.
5. **`MainActivity.kt` compiles clean with no WebView references.** Fixed
   this session: `MainActivity` still implemented `CobaltWebView.Listener`
   and referenced `binding.webView` in `setupWebView()`/`checkClipboard()`/
   `submitUrl()`, even though `activity_main.xml` has had no `WebView`
   element since the bottom-nav rewrite — this did not compile.
   `CobaltWebView.kt`/`CobaltJsBridge.kt` are deleted; `submitUrl()` now
   navigates to the Home tab and passes the pasted/shared URL as a nav
   argument (`pending_url`) for Phase 3's `HomeFragment` to consume — real
   routing, not a fake "handled" state.
6. **The download-queue badge observer now actually runs.** Found this
   session: `queueViewModel.activeDownloads.observe(...)` (drives the FAB
   badge count) was only ever registered inside the dead `setupWebView()`,
   which `onCreate()` never called — so the badge never updated. Moved
   directly into `onCreate()`.

---

### Phase 2 — Shorts feed screen ✅ done (Session 5)
**Superseded the old Phase 2 spec below.** The original spec assumed a
single small mock/kiosk data source. Per explicit user direction this
session, the real requirement is a feed merged from three independent
YouTube-catalog backends — Innertube (direct), NewPipeExtractor, and public
Invidious instances — cyclically interleaved so no single backend going
down or rate-limiting empties the feed, and so the feed has real volume
instead of one small trending/kiosk list.

**Why three sources needed a shared "query feeder" instead of a Shorts
endpoint:** neither NewPipeExtractor nor Invidious exposes a working
Shorts-only endpoint (verified this session — NewPipeExtractor's YouTube
service registers exactly one kiosk, "Trending", no Shorts kiosk exists;
Invidious's `/api/v1/trending?type=Shorts` filter is a confirmed-broken
upstream param, iv-org/invidious#2982). So all three sources run **search**
against a shared rotating pool of query terms
(`shorts/source/ShortsQueryFeeder.kt`) and keep only <=90s results — search
returns a much larger, more varied candidate pool per call than any single
trending/kiosk/popular list, and rotating the query pool means successive
refreshes sweep the catalog instead of hammering the same 2–3 terms. Even
Innertube, which *does* have a genuine Shorts-shelf signal
(`reelItemRenderer` on the home feed), uses the query feeder as its primary
volume driver and the shelf only as a secondary top-up, for the same reason.

**Files (all real, no stubs):**
- `shorts/model/ShortItem.kt` — unified item model all three sources map into
- `shorts/source/ShortsSource.kt` — common interface
- `shorts/source/ShortsQueryFeeder.kt` — shared rotating query-term pool
- `shorts/source/InnertubeShortsSource.kt` — direct OkHttp calls to
  `youtubei/v1/search` + `youtubei/v1/browse` (WEB client) for discovery,
  `youtubei/v1/player` (ANDROID client, unciphered URLs) to resolve
- `shorts/source/NewPipeShortsSource.kt` — NewPipeExtractor search +
  Trending-kiosk top-up
- `shorts/source/InvidiousShortsSource.kt` — public-instance `/api/v1/search`
  + `/api/v1/popular` top-up, with per-call instance failover
- `shorts/source/OkHttpNewPipeDownloader.kt`, `NewPipeInit.kt` — required
  glue so NewPipeExtractor uses the project's existing OkHttp stack instead
  of a second HTTP client
- `shorts/db/ShortsCacheEntity.kt`, `ShortsCacheDao.kt`, `ShortsDatabase.kt`
  — a genuinely new, separate Room database from `download/DownloadDatabase.kt`
  (caching a resolved feed is a different domain from the download queue;
  see "Unified enhancement" above — there's no existing table to extend
  here)
- `shorts/ShortsFeedRepository.kt` — round-robin merge, de-dupe, cache
  read-through/fallback
- `ui/shorts/ShortsViewModel.kt` — real, coroutine-driven, `AndroidViewModel`
- `ui/shorts/ShortsAdapter.kt` — `ListAdapter` + `DiffUtil`
- `ui/shorts/ShortsFragment.kt` — moved to the spec-correct package path
  (was `ui/ShortsFragment.kt`, also had missing imports and did not compile
  — fixed this session, see "Bugs found and fixed" below)
- `res/layout/fragment_shorts.xml` — `ViewPager2` + loading indicator
- `res/layout/item_short_video.xml` — `androidx.media3.ui.PlayerView`
  (replaces `VideoView`, which can't play HLS/DASH) + icon rail + caption

**Bugs found and fixed this session (pre-existing, not introduced):**
1. `ui/ShortsFragment.kt` referenced `ViewModelProvider`, `ShortsViewModel`,
   and `ShortsAdapter` with **no imports for any of them** — did not
   compile. Root cause of the "hardcoded fake data" issue flagged in the
   Session 4 handover was masked by this: the file never built, so the fake
   data was never actually the live behavior on a real build.
2. `androidx.viewpager2:viewpager2` was **never added to
   `app/build.gradle.kts`** despite `fragment_shorts.xml` already using a
   `ViewPager2` — another compile break nobody had hit yet because #1 broke
   the build first.

**Dependencies added:** `androidx.viewpager2:viewpager2:1.1.0`,
`androidx.media3:media3-exoplayer:1.4.1` (+ `-hls`, `-dash`, `-ui`),
`com.github.TeamNewPipe:NewPipeExtractor:v0.24.6` (via JitPack — added
`https://jitpack.io` to `settings.gradle.kts`, the only non-Google/Maven-
Central repo in the project, since NewPipeExtractor isn't published to Maven
Central).

**Definition of Done:**
1. ✅ `ShortsFragment` is reachable by tapping the Shorts tab
   (`nav_graph.xml` updated to the corrected package path).
2. ✅ `ViewPager2` (vertical) bound to a `ListAdapter` backed by
   `ShortsFeedRepository`'s live merged feed — not mock data.
3. ✅ Each item has a real video surface (`PlayerView`, driven by a single
   shared `ExoPlayer` per the standard Shorts-feed pattern — see
   `ShortsFragment.playAt()`), a working like action (persists to
   `ShortsCacheEntity.isLiked`), a working save action (routes through the
   *existing* `DownloadService.startHttps` — no parallel download path), and
   a working share action (real `ACTION_SEND` intent).
4. ✅ Feed is cyclically merged across all three sources and falls back to
   the Room cache if all three fail on a given refresh — never silently
   empty.
5. ✅ Infinite scroll: `loadMore()` fires a few items before the end of what's
   loaded.

**Known limitations, honestly stated (see HANDOVER for the full list):**
- Not yet compiled/run on a device or emulator this session — no Android SDK
  was available in the sandbox this was written in. **Verify this builds
  before trusting it further** (see "Immediate next steps").
- The <=90s duration heuristic will occasionally misclassify a short
  non-Shorts video as a Short and vice versa; there is no better signal
  available from any of the three backends for search-derived candidates.
- Innertube client keys/versions and the Invidious instance list are
  hardcoded constants that will drift over time (documented in-code where
  they live).
- The query-term pool (`ShortsQueryFeeder`) is a static seed list, not a live
  trending-topics feed — see Phase 14.

---

### Phase 3 — Home screen shell + paste-link UI (no network yet) ✅ done (Session 5)
**Files:**
- `ui/home/HomeFragment.kt`, `ui/home/HomeViewModel.kt` (moved to the
  spec-correct `ui/home/` package; the old `ui/HomeFragment.kt` is deleted)
- `res/layout/fragment_home.xml` — real `TextInputLayout` paste-link field +
  submit button + status line + a reserved (currently empty) feed container.
  Also removed a dead `<data><variable ...></data>` data-binding block this
  file had — the project only enables `viewBinding` in `build.gradle.kts`,
  not `dataBinding`, so that block was inert and never actually functioned;
  found while touching this file, not introduced by it.

**Definition of Done:**
1. ✅ Home screen has a visible paste-link field (`etLinkInput`) distinct from
   the Shorts feed, reachable via the bottom nav (already wired — this phase
   didn't touch nav_graph.xml's destination list, only the fragment class it
   points at).
2. ✅ `HomeFragment` reads the `pending_url` nav argument on launch (set by
   `MainActivity.submitUrl()`) and populates the paste-link field with it via
   `HomeViewModel.setPendingUrl()`.
3. ✅ Submitting currently shows a real, honest status message — "Link
   resolution isn't implemented yet (Phase 4)." — rather than faking a
   network response. Basic input validation (empty / not http(s)) also
   surfaces real, distinct messages. This is intentionally *not* wired to
   any resolver yet; that's Phase 4's job specifically so it can be a real
   network call from the start per "No stubs, no placeholders" above.

---

### Phase 4 — Real link resolution (LinkResolverRepository) ✅ done (Session 6)
**Files (actual paths — differ from the original spec below, see note):**
- `app/src/main/java/com/cobalt/android/link/LinkResolverRepository.kt` —
  a Phase-3-era placeholder version of this file already existed at this
  path (added out of sequence, before Phase 3 landed) with a blocking,
  non-coroutine `execute()` call against a `{instance}/api/resolve?url=`
  GET contract that doesn't match any real cobalt instance. This phase
  rewrote it: `suspend fun resolve(url): ResolveResult` (sealed
  `Success`/`Error`), run on `Dispatchers.IO` via `withContext`, POSTing
  JSON to `{instance}/` per the real cobalt v7+ API contract
  (`docs/api.md` in imputnet/cobalt) and handling all of `error`,
  `rate-limit`, `picker`, `redirect`, `tunnel`, `stream`, and
  `local-processing` statuses — see in-file doc comment for the full
  contract this assumes.
- `app/src/main/java/com/cobalt/android/ui/home/HomeViewModel.kt` — moved
  from plain `ViewModel` to `AndroidViewModel` (needs a `Context` for
  `LinkResolverRepository`/`SettingsRepository`, same reason
  `ShortsViewModel` is an `AndroidViewModel`). `onSubmit()` now launches a
  real `repository.resolve(url)` call in `viewModelScope`, exposes
  `isResolving: LiveData<Boolean>` and `resolveResult:
  LiveData<ResolveResult?>` in addition to the existing `statusMessage`.
- `app/src/main/java/com/cobalt/android/ui/home/HomeFragment.kt` —
  observes `isResolving` to disable the submit button/field while a
  resolve is in flight (prevents double-submit against a slow/unreachable
  instance). Does **not** yet render `resolveResult` — that's Phase 6's
  resolution-picker UI; this phase's job is only to guarantee the data is
  real and present in the ViewModel, not to display it.

**Note on the pre-existing file location:** the original spec above named
`repository/LinkResolverRepository.kt`; the file that actually existed
(added in commit `957411f`, before Phase 3) lives at
`link/LinkResolverRepository.kt` instead. Kept the existing path rather
than moving it — no functional reason to relocate it, and moving it would
have been a second unrelated change bundled into this phase.

**Definition of Done:**
1. ✅ Submitting a link from Phase 3's UI (manually or via `pending_url`)
   invokes `LinkResolverRepository.resolve()` with a real HTTP POST — not a
   TODO/comment.
2. ✅ A successful resolve is held in `HomeViewModel.resolveResult` ready for
   Phase 6 to display — the actual picker UI is still Phase 6, but the data
   is real (parsed from a real HTTP response), not mocked.
3. ✅ Resolution failures (bad URL, unreachable instance, HTTP error,
   unparseable body, rate-limit, empty picker) each surface a distinct,
   real error message via `statusMessage` — not a silent no-op or one
   generic "something went wrong".

**Known limitations, honestly stated (see HANDOVER for the full list):**
- **Not verified against a live cobalt instance.** This sandbox has no
  network egress to arbitrary hosts (only a fixed domain allowlist —
  github.com, pypi.org, npmjs.com, etc. — no `cobalt.tools` or any other
  self-hosted instance), so the request/response handling is structurally
  correct against the *documented* API contract but has not actually been
  exercised against a real server this session. Confirm this against a
  live instance before trusting it further — see HANDOVER "Immediate next
  steps".
- The API contract assumes a cobalt v7+-style instance. Older instances
  (pre-API-rewrite) use a different response shape entirely; this
  repository does not attempt to detect or support those.
- `filenameFromUrl()`'s extension/MIME-type guessing is a best-effort
  fallback for when an instance's response omits `filename` — it is not a
  substitute for a real `Content-Type`/`Content-Disposition` header read,
  which this phase does not add (the resolve call only reads the JSON
  body, not headers). Revisit if Phase 6 testing shows filenames/mime
  types coming through wrong often enough to matter.

---

### Phase 5 — ResolutionCacheEntity (the download engine itself already exists) ✅ done
Landed across three commits after a merge conflict from two Hermes
sessions building this phase independently: the entity/DAO were
consolidated into one implementation at the spec'd package
(`db.entities`/`db`, formats-list + freshness design, not the earlier
single-URL stub), `DownloadDatabase.kt`'s missing `Context` import was
fixed, and schema bumped 1→2 with a real `Migration`.
`LinkResolverRepository.resolve()` now reads the cache first (5-minute
freshness window — resolved URLs can be short-lived signed links) and
writes through on every successful resolve, satisfying DoD item 2.

**Do NOT build a new download engine.** `DownloadRecord`/`DownloadDao`/
`DownloadRepository`/`DownloadService`/`RetryDownloadWorker`/
`NotificationHelper`/`MediaStoreWriter` already implement queueing,
progress, retries, and notifications end-to-end via
`DownloadService.startHttps(...)`. This phase only adds the persistence
piece that's genuinely missing so Phase 6's picker doesn't have to
re-resolve on every open.

**Files:**
- `app/src/main/java/com/cobalt/android/db/entities/ResolutionCacheEntity.kt`
  — per-source available formats. Add it to `DownloadDatabase.kt`'s
  existing entity list (do not create a new database — contrast with
  Phase 2's `ShortsDatabase`, which *is* new because there was nothing to
  extend for that domain; this domain already has a database).
- `app/src/main/java/com/cobalt/android/db/ResolutionCacheDao.kt`

**Definition of Done:**
1. `ResolutionCacheEntity` + its DAO exist and are added to the *existing*
   `DownloadDatabase.kt` entity list — not a new database.
2. `LinkResolverRepository` (Phase 4) writes through this cache on a
   successful resolve and reads from it before re-hitting the network for a
   URL resolved recently.

---

### Phase 6 — Resolution picker UI + wiring to the existing download engine ✅ done
**Files (actual — matches spec):**
- `app/src/main/java/com/cobalt/android/ui/downloads/ResolutionPickerDialog.kt`
  — bottom sheet listing resolutions/formats from `HomeViewModel.resolveResult`
  (backed by Phases 4–5), matching the Vidmate-style "pick quality before
  download" step. Reads the ViewModel directly (activity-scoped, shared with
  `HomeFragment`) rather than passing `ResolvedFormat` through Bundle
  arguments, so it didn't need to become Parcelable for a same-process hand-off.
- `app/src/main/java/com/cobalt/android/ui/downloads/ResolutionFormatAdapter.kt`
  — `ListAdapter`/`DiffUtil`, same pattern as `DownloadAdapter`.
- `app/src/main/res/layout/sheet_resolution_picker.xml`,
  `item_resolution_format.xml` — styled to match `sheet_download_queue.xml`/
  `item_download.xml` (same colors, IBM Plex Mono font).
- `HomeFragment` switched from `viewModels()` to `activityViewModels()` so the
  dialog (shown via `childFragmentManager`) shares the same `HomeViewModel`
  instance instead of getting its own.

**Definition of Done:**
1. ✅ `ResolutionPickerDialog` is shown by `HomeFragment` observing
   `resolveResult`, immediately after `LinkResolverRepository` resolves a
   link — Phase 3 → 4 → 6 wiring is real end-to-end.
2. ✅ Confirming a resolution calls `DownloadService.startHttps(...)` with the
   chosen format's direct URL/filename/mimeType — the exact same call
   `ShortsViewModel.downloadToDevice()` already uses; both paths converge on
   one `DownloadService`, not two.
3. ✅ Notification-on-progress path untouched — still driven entirely by
   `NotificationHelper` inside `DownloadService`, nothing in this phase
   touches that code.

---

### Phase 7 — Downloads / Library screen: list + live progress ✅ done (pre-existing, now recorded)
**Files (actual — differ from the spec below, see note):**
- `app/src/main/java/com/cobalt/android/ui/DownloadQueueSheet.kt` +
  `DownloadQueueViewModel.kt` — a bottom sheet, not a full-screen
  `DownloadsLibraryFragment`. Already queries `DownloadRepository.allDownloads`/
  `activeDownloads` (real `Room` `LiveData`, not mock) and already reflects
  live progress: `DownloadService` calls `updateProgress()` mid-download,
  which Room emits straight through to `DownloadAdapter`. Building a second,
  parallel full-screen version alongside this would violate "Unified
  enhancement, NOT a parallel rebuild" the same way the Phase 5 duplicate
  entity did — so this phase records the existing implementation as meeting
  the DoD rather than building a redundant one.
- `app/src/main/res/layout/sheet_download_queue.xml` + `item_download.xml`
  (not `fragment_downloads_library.xml`).

**Definition of Done:**
1. ✅ Real `DownloadDao`/`DownloadRepository` data, live progress reflected.
2. ✅ Reachable via `fabQueue` (a FAB, bottom|end) in `MainActivity` — the
   choice recorded here per the DoD's own instruction: FAB was already the
   established entry point, not a top-nav icon or bottom-nav tab. Consistent
   single entry point, nothing else invokes `DownloadQueueSheet`.

---

### Phase 8 — Downloads / Library screen: tap-to-play
**Files:** extends Phase 7's fragment/layout; no new top-level files
required unless a dedicated player screen is chosen over an inline surface.

**Definition of Done:**
1. Tapping a completed item plays the local file via a real video player
   surface — reuse Media3/ExoPlayer (already a dependency as of Phase 2;
   don't add a second video-playback library).
2. Playback respects standard lifecycle (pauses on backgrounding, releases
   the player on screen exit) — same discipline as Phase 2's Shorts player,
   see Phase 18 for the shared hardening pass.

---

### Phase 9 — History & Likes: entities + DAOs
**Files:**
- `app/src/main/java/com/cobalt/android/db/entities/HistoryEntity.kt`
- `app/src/main/java/com/cobalt/android/db/entities/LikedEntity.kt`
- `app/src/main/java/com/cobalt/android/db/daos/HistoryDao.kt`
- `app/src/main/java/com/cobalt/android/db/daos/LikedDao.kt`

**Definition of Done:**
1. Entities + DAOs exist and are wired into a real database (extend
   `DownloadDatabase.kt` if you decide history/likes belong with downloads,
   or justify a separate database in this file if not — either is
   defensible, but state the choice explicitly, matching the precedent set
   in Phase 2/5 above).
2. No UI wiring yet — that's Phases 10–11. This phase is data-layer only,
   scoped small on purpose per the 20-phase restructure.

---

### Phase 10 — History & Likes: real write-side integration
**Definition of Done:**
1. Watching a Shorts item (Phase 2) writes a `HistoryEntity` row — real
   integration into `ShortsFragment`/`ShortsViewModel`, not a standalone
   unused DAO.
2. Completing a download (Phase 6/7) writes a `HistoryEntity` row too, so
   history covers both surfaces.
3. Tapping "like" on a Shorts item (the icon rail from Phase 2, currently
   writing only to `ShortsCacheEntity.isLiked`) **also** writes a
   `LikedEntity` row — Phase 2's cache-local like flag was intentionally
   scoped that way because `LikedEntity` didn't exist yet; this phase
   connects the two rather than leaving two disconnected "liked" concepts.

---

### Phase 11 — History & Likes: UI screen
**Files:**
- `app/src/main/java/com/cobalt/android/ui/history/HistoryFragment.kt`
  (watched Shorts + resolved links + liked items, most-recent first)

**Definition of Done:**
1. `HistoryFragment` displays real DB-backed history/likes from Phase
   9–10's tables, reachable from the UI (settings menu entry or
   profile-style surface — pick one, record the choice here).

---

### Phase 12 — Settings: preference storage layer
**Files:**
- `app/src/main/java/com/cobalt/android/util/SettingsRepository.kt`
  (already exists for `cobaltInstanceUrl` — extend it) or a
  `PreferenceEntity.kt`/DataStore addition if the existing repository's
  storage mechanism doesn't scale to the new keys below. Pick one approach
  and use it consistently, do not mix both.

**Definition of Done:**
1. At minimum: default download resolution, download location, and
   dark/light/dynamic theme are new persisted keys alongside the existing
   `cobaltInstanceUrl`.
2. No UI yet — Phase 13 builds the screen. This phase just makes the values
   real and persisted.

---

### Phase 13 — Settings: UI screen + theme wiring
**Files:**
- `app/src/main/java/com/cobalt/android/ui/settings/SettingsFragment.kt`
- `app/src/main/res/xml/settings_preferences.xml` (if using
  `PreferenceFragmentCompat`)

**Definition of Done:**
1. `SettingsFragment` exposes and edits Phase 12's keys.
2. Changing the theme toggle actually applies (Material You dynamic color
   from Phase 1 responds to it) — this is the one piece of Phase 13 that
   must visibly work, not just persist a value nothing reads.

---

### Phase 14 — Settings: wiring into resolution defaults + Shorts sources
**Definition of Done:**
1. Changing the default resolution (Phase 12) actually changes what
   `ResolutionPickerDialog` (Phase 6) pre-selects.
2. The Invidious instance list (`InvidiousShortsSource.DEFAULT_INSTANCES`,
   currently a hardcoded constant — see Phase 2) becomes user-configurable
   from Settings, with the hardcoded list as the shipped default.
3. Optional but recommended: expose `ShortsQueryFeeder`'s seed-query pool
   as editable too, so a user (or a future update) can tune what "trending"
   means for their feed without a code change.

---

### Phase 15 — Shorts feed hardening: pagination, backoff, rate limits
**Definition of Done:**
1. `ShortsFeedRepository.loadMore()` (Phase 2) is verified under real
   repeated use — confirm de-dupe holds across many pages, not just the
   first two.
2. Each of the three sources gets basic backoff on repeated failures
   (don't hammer a dead Invidious instance or a 403'ing Innertube key every
   single refresh) — simple exponential backoff per source is sufficient,
   this doesn't need to be sophisticated.
3. Confirm the `SOURCE_TIMEOUT_MS` guard in `ShortsFeedRepository` (Phase 2)
   is tuned reasonably against real network conditions, not just the
   12s placeholder value picked without live testing.

---

### Phase 16 — Shorts feed polish: offline + engagement
**Definition of Done:**
1. With no network at all, the Shorts feed shows the Room-cached items
   (Phase 2's fallback path) with a visible "offline" indicator, rather than
   silently looking identical to a live feed.
2. History writes from Phase 10 are confirmed actually firing from real
   Shorts playback (watch a few items, check `HistoryEntity` rows appear) —
   this is a verification step as much as a build step.

---

### Phase 17 — Performance: loading placeholders
**Definition of Done:**
1. Skeleton/shimmer loading placeholders are present on Home (Phase 3),
   Shorts (Phase 2), and Downloads (Phase 7) screens while their respective
   data loads (per the original scaffold requirement).

---

### Phase 18 — Performance: player lifecycle discipline
**Definition of Done:**
1. `ShortsFragment`'s shared `ExoPlayer` (Phase 2) and the Downloads
   tap-to-play surface (Phase 8) both release/pause correctly on
   backgrounding and screen exit — verified, not assumed.
2. The next Shorts item preloads (or at least its stream URL pre-resolves)
   shortly before it becomes visible, so swiping doesn't show a visible
   buffering gap on a decent connection.

---

### Phase 19 — Full "no stubs" audit
**Files:** no new required files; this phase is verification only.

**Definition of Done:**
1. `grep -rn "TODO" app/src/main/java` returns nothing from this project's
   own code (excluding third-party library sources under `build/` or
   caches).
2. **Grep alone is not sufficient** — Session 4 found `ShortsViewModel.kt`
   was fully hardcoded fake data with no literal `TODO` anywhere in it.
   Every file created across Phases 1–18 must be read by eye against its
   own Definition of Done before this phase can close.
3. `git log` shows a real commit backing every phase above (cross-check
   against `state.json`'s `last_commit_sha` history if available).

---

### Phase 20 — Final gate: architecture_complete
**Definition of Done (all must be true):**
1. Phases 1–19 above are all individually done per their own Definition of
   Done, verified by inspecting the actual repo, not by trusting a prior
   session's summary.
2. Only once ALL of the above are true does Hermes set
   `architecture_complete: true` in `state.json`. This is the ONLY
   condition under which CI/build-error monitoring (MAINTAIN MODE) begins.

---

## Non-negotiable rules for every phase (carried over, still apply)
- Full working implementation only — no stubs, no TODOs, no placeholder/
  fake logic left in committed code. See "No stubs, no placeholders" above.
- Never modify `app/src/main/res/drawable/bg_fab.xml` unless the task is
  specifically about the FAB button.
- Never modify a file in this cycle that wasn't read in this same cycle.
- One bounded unit of work per cycle — do not attempt an entire phase in
  a single cycle. Prefer a smaller fully-working piece over a larger
  half-working one.
- Commit and push immediately after each file write, before starting the
  next file.
