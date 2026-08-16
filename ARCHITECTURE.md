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
- Room for local persistence
- Material Components (already a dependency per `build.gradle.kts`)
- WorkManager for background downloads
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
├── download/             DownloadService, DownloadWorker, DownloadNotificationManager
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
   items referencing `bottom_nav_menu.xml`.
2. `MainActivity.kt` contains an `onItemSelectedListener` (or equivalent)
   that actually swaps the fragment container's content — not a stub/TODO.
3. All three destination fragments exist as real files (even if their body
   is minimal) — see Phases 2-3 for Home/Shorts specifically.
4. `bg_fab.xml` is untouched (oval FAB background, not an icon).

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
  + a `WebView` (or feed of recent/trending sources) below it, matching the
  existing `MainActivity` WebView-centric approach
- `app/src/main/java/com/cobalt/android/repository/LinkResolverRepository.kt`
  — takes a pasted URL, resolves available formats/resolutions (stub the
  network call if the real resolver isn't ready yet, but the interface and
  call site must be real)

**Definition of Done:**
1. Home screen has a visible paste-link/search entry field distinct from
   the Shorts feed.
2. Submitting a link invokes `LinkResolverRepository` (real call, not a
   TODO/comment).
3. A successful resolve navigates to or surfaces the format/resolution
   picker from Phase 4 — the two phases must actually connect.

---

### Phase 4 — Download engine (queue, resolution picker, worker, notifications)
**Files:**
- `app/src/main/java/com/cobalt/android/db/entities/DownloadEntity.kt` —
  fields: id, sourceUrl, title, thumbnailUrl, resolution, status
  (queued/downloading/paused/failed/complete), progress, filePath, createdAt
- `app/src/main/java/com/cobalt/android/db/daos/DownloadDao.kt`
- `app/src/main/java/com/cobalt/android/db/entities/ResolutionCacheEntity.kt`
  — per-source available formats, to avoid re-resolving on every open
- `app/src/main/java/com/cobalt/android/ui/downloads/ResolutionPickerDialog.kt`
  — bottom sheet/dialog listing available resolutions/formats for a
  resolved link, matching the Vidmate-style "pick quality before download"
  step
- `app/src/main/java/com/cobalt/android/download/DownloadWorker.kt` —
  `CoroutineWorker` performing the actual download, updating `DownloadDao`
  progress as it goes
- `app/src/main/java/com/cobalt/android/download/DownloadNotificationManager.kt`
  — persistent progress notification per active download

**Definition of Done:**
1. `DownloadEntity` + `DownloadDao` exist with the fields above, and
   `CobaltDatabase.kt` includes `DownloadEntity` in its entity list.
2. `ResolutionPickerDialog` is actually shown after `LinkResolverRepository`
   resolves a link (Phase 3 → Phase 4 wiring is real, not aspirational).
3. Confirming a resolution enqueues a `DownloadWorker` via WorkManager (not
   a fire-and-forget coroutine with no persistence/retry).
4. A notification with progress appears while a download is active.

---

### Phase 5 — Downloads / Library screen
**Files:**
- `app/src/main/java/com/cobalt/android/ui/downloads/DownloadsLibraryFragment.kt`
- `app/src/main/java/com/cobalt/android/ui/downloads/DownloadsViewModel.kt`
- `app/src/main/res/layout/fragment_downloads_library.xml` — RecyclerView
  list of completed + in-progress downloads, thumbnail + title + progress
  or file size, tap-to-play

**Definition of Done:**
1. Screen queries `DownloadDao` (real Flow/LiveData, not mock data) and
   reflects live progress for in-progress items.
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
