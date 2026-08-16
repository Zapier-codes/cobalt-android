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

## Build Sequencing — 8 phases, in strict order

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

### Phase 2 — Shorts feed screen
**Files:**
- `app/src/main/java/com/cobalt/android/ui/shorts/ShortsFragment.kt`
  (already exists — verify it's wired into MainActivity's nav, not just
  created standalone)
- `app/src/main/java/com/cobalt/android/ui/shorts/ShortsViewModel.kt`
- `app/src/main/java/com/cobalt/android/ui/shorts/ShortsAdapter.kt` — a
  `RecyclerView.Adapter` using `ViewPager2` with vertical orientation for
  full-screen swipeable video items (TikTok/Shorts-style paging, one video
  per screen)
- `app/src/main/res/layout/fragment_shorts.xml` — `ViewPager2` filling the
  screen
- `app/src/main/res/layout/item_short_video.xml` — one page: video surface
  + right-side vertical icon rail (like, save/download, share) + bottom
  caption/source-link overlay
- `app/src/main/java/com/cobalt/android/db/entities/ShortsCacheEntity.kt`

**Definition of Done:**
1. `ShortsFragment` is reachable by tapping the Shorts tab in the running
   nav (not just instantiated in isolation).
2. `ViewPager2` with vertical orientation is present and bound to an
   adapter backed by a real (even if small/mock) data source.
3. Each item layout has: a video surface view, a like/save/download action,
   and a share action — matching the reference UX (icon rail, not a
   traditional list row).

---

### Phase 3 — Home / Discovery screen (link-paste + browse entry point)
**Files:**
- `app/src/main/java/com/cobalt/android/ui/home/HomeFragment.kt`
- `app/src/main/java/com/cobalt/android/ui/home/HomeViewModel.kt`
- `app/src/main/res/layout/fragment_home.xml` — top search/paste-link bar
  + a feed of recent/trending sources below it. **No `WebView`** — replaces
  the current placeholder (`tvPlaceholder` + a stray `TODO` comment).
- `app/src/main/java/com/cobalt/android/repository/LinkResolverRepository.kt`
  — takes a pasted URL, calls the cobalt instance API directly (OkHttp,
  `settings.cobaltInstanceUrl` from `SettingsRepository`) to resolve
  available formats/resolutions. This is a real network call from the
  start — there is no WebView fallback to lean on anymore, so this cannot
  be stubbed even temporarily without violating "no stubs, no
  placeholders" above.

**Definition of Done:**
1. Home screen has a visible paste-link/search entry field distinct from
   the Shorts feed.
2. `HomeFragment` reads a `pending_url` nav argument on launch (set by
   `MainActivity.submitUrl()` for share-intent/clipboard/shortcut-triggered
   URLs — see Phase 1 item 5) and auto-submits it if present.
3. Submitting a link (manually or via `pending_url`) invokes
   `LinkResolverRepository` with a real HTTP call — not a TODO/comment.
4. A successful resolve navigates to or surfaces the format/resolution
   picker from Phase 4 — the two phases must actually connect.

---

### Phase 4 — Resolution picker (the download engine itself already exists)
**Do NOT build a new download engine.** `DownloadRecord`/`DownloadDao`/
`DownloadRepository`/`DownloadService`/`RetryDownloadWorker`/
`NotificationHelper`/`MediaStoreWriter` already implement queueing,
progress, retries, and notifications end-to-end via
`DownloadService.startHttps(...)`. This phase only needs to add the piece
that's genuinely missing: letting the user pick a resolution/format before
that call happens.

**Files:**
- `app/src/main/java/com/cobalt/android/db/entities/ResolutionCacheEntity.kt`
  — per-source available formats, to avoid re-resolving on every open. Add
  it to `DownloadDatabase.kt`'s existing entity list (do not create a new
  database).
- `app/src/main/java/com/cobalt/android/db/ResolutionCacheDao.kt`
- `app/src/main/java/com/cobalt/android/ui/downloads/ResolutionPickerDialog.kt`
  — bottom sheet listing resolutions/formats returned by
  `LinkResolverRepository` (Phase 3), matching the Vidmate-style "pick
  quality before download" step.

**Definition of Done:**
1. `ResolutionCacheEntity` + its DAO exist and are added to the *existing*
   `DownloadDatabase.kt` entity list — not a new database.
2. `ResolutionPickerDialog` is actually shown after `LinkResolverRepository`
   resolves a link (Phase 3 → Phase 4 wiring is real, not aspirational).
3. Confirming a resolution calls `DownloadService.startHttps(...)` with the
   chosen format's direct URL/filename/mimeType — reusing the existing
   service, not a new worker.
4. A notification with progress appears while a download is active — this
   already happens via `NotificationHelper` inside `DownloadService`; this
   step just confirms nothing broke that path.

---

### Phase 5 — Downloads / Library screen
**Files:**
- `app/src/main/java/com/cobalt/android/ui/downloads/DownloadsLibraryFragment.kt`
- `app/src/main/java/com/cobalt/android/ui/downloads/DownloadsViewModel.kt`
- `app/src/main/res/layout/fragment_downloads_library.xml` — RecyclerView
  list of completed + in-progress downloads, thumbnail + title + progress
  or file size, tap-to-play

**Definition of Done:**
1. Screen queries the existing `DownloadDao`/`DownloadRepository`
   (`allDownloads`/`activeDownloads` `LiveData`, already implemented — real
   data, not mock) and reflects live progress for in-progress items.
2. Tapping a completed item plays the local file (via a video player
   surface — reuse whatever player component exists, or ExoPlayer if none
   does yet).
3. Reachable from somewhere in the nav (top-nav downloads icon per the
   original scaffold, or a 4th bottom-nav/overflow entry — pick one and be
   consistent).

---

### Phase 6 — History & Likes
**Files:**
- `app/src/main/java/com/cobalt/android/db/entities/HistoryEntity.kt`
- `app/src/main/java/com/cobalt/android/db/entities/LikedEntity.kt`
- `app/src/main/java/com/cobalt/android/db/daos/HistoryDao.kt`
- `app/src/main/java/com/cobalt/android/db/daos/LikedDao.kt`
- `app/src/main/java/com/cobalt/android/ui/history/HistoryFragment.kt`
  (watched Shorts + resolved links, most-recent first)

**Definition of Done:**
1. Watching a Shorts item (Phase 2) writes a `HistoryEntity` row — real
   integration, not a standalone unused DAO.
2. Tapping "like" on a Shorts item (the icon rail from Phase 2) writes a
   `LikedEntity` row.
3. `HistoryFragment` displays real DB-backed history, reachable from the
   UI (settings menu entry or profile-style surface — pick one).

---

### Phase 7 — Settings & preferences
**Files:**
- `app/src/main/java/com/cobalt/android/db/entities/PreferenceEntity.kt`
  (or DataStore, if preferred over Room for key-value settings — pick one
  approach and use it consistently, do not mix both)
- `app/src/main/java/com/cobalt/android/ui/settings/SettingsFragment.kt`
- `app/src/main/res/xml/settings_preferences.xml` (if using
  `PreferenceFragmentCompat`)

**Definition of Done:**
1. At minimum: default download resolution, download location, and
   dark/light/dynamic theme toggle are present and persisted.
2. Changing the theme toggle actually applies (Material You dynamic color
   from Phase 1 responds to it).
3. Changing the default resolution actually changes what
   `ResolutionPickerDialog` (Phase 4) pre-selects.

---

### Phase 8 — Polish & performance
**Files:** no new required files; this phase is verification + refinement
of everything above.

**Definition of Done (all must be true):**
1. Skeleton/shimmer loading placeholders are present on Home, Shorts, and
   Downloads screens while their respective data loads (per the original
   scaffold requirement).
2. No `TODO`/stub implementations remain in any file created in phases 1-7
   — `grep -rn "TODO" app/src/main/java` returns nothing from this
   project's own code.
3. `git log` shows a real commit backing every phase above (cross-check
   against `state.json`'s `last_commit_sha` history if available).
4. Only once ALL of the above are true does Hermes set
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
