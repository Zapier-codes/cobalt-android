# Cobalt-Android — Handover (Session 5 → 6)

**Start by cloning the repo fresh: `git clone https://github.com/Zapier-codes/cobalt-android.git`**
Do not trust any file content quoted in this document or prior handovers as
current — clone, then read the real files. This document explains what
changed and why, not what's guaranteed to still be true by the time you
read it.

**This file itself should now be committed at the repo root as
`HANDOVER.md`.** If you don't find it there, the patch from this session
hasn't been applied/pushed yet — stop and flag that before assuming Session
5's work (including this document) is live. See "Verify this landed" below.

## What Session 5 actually did

1. **Confirmed Session 4's work landed** before starting anything new: cloned
   fresh, verified commit `743ba46` (Session 4's WebView-removal + badge-fix
   + ARCHITECTURE.md rewrite) was genuinely at HEAD, verified by reading the
   actual file contents (not just trusting the commit message) that
   `MainActivity.kt` no longer implements `CobaltWebView.Listener` and the
   badge observer is in `onCreate()`.
2. **Found two build-breaking bugs Session 4's audit missed**, both
   pre-existing, neither introduced this session:
   - `ui/ShortsFragment.kt` referenced `ViewModelProvider`, `ShortsViewModel`,
     and `ShortsAdapter` with no imports for any of them — the file did not
     compile. This means the "hardcoded fake data" `ShortsViewModel` Session
     4 flagged was never actually reachable on a real build; the module was
     broken before it got that far.
   - `androidx.viewpager2:viewpager2` was never added to
     `app/build.gradle.kts` despite `fragment_shorts.xml` already declaring a
     `ViewPager2` — a second, independent compile break.
3. **Completed Phase 2 (Shorts feed) for real**, per explicit user direction
   that the feed must merge three sources — Innertube (direct), NewPipe
   (NewPipeExtractor), and public Invidious instances — cyclically, so no
   single backend dying empties the feed, and with enough real volume that
   the feed doesn't run dry after a screen or two of scrolling.
4. **Restructured `ARCHITECTURE.md` from 8 phases to 20**, per explicit user
   direction, so a single session can realistically finish a phase's
   Definition of Done in one sitting instead of leaving `state.json` and the
   real repo disagreeing about progress. No phase's original requirements
   were dropped — every file and Definition-of-Done bullet from the 8-phase
   version now lives somewhere in the 20-phase version (see the "Restructured
   from 8 phases to 20" note directly under "Build Sequencing" in
   `ARCHITECTURE.md` for the old→new phase mapping).
5. **Committed locally, NOT pushed** — same constraint as Session 4: no push
   credentials were available in the sandbox this session ran in. A
   `git am`-compatible patch was produced for you to apply and push from your
   own device (see below).

## The Shorts feed design, in short (full detail is in ARCHITECTURE.md Phase 2)

Neither NewPipeExtractor nor Invidious has a real "Shorts" endpoint:
- NewPipeExtractor's YouTube service only registers one kiosk, "Trending" —
  verified against upstream this session, there's no Shorts kiosk to call.
- Invidious's `/api/v1/trending?type=Shorts` filter is a confirmed-broken
  upstream param (iv-org/invidious#2982) that instances generally ignore.

So the actual mechanism, per your direction mid-session, is: all three
sources run **search** against a shared rotating pool of query terms
(`shorts/source/ShortsQueryFeeder.kt` — a static seed list of ~30 broad,
evergreen categories like "funny shorts", "gaming clips", "life hack", cycled
via an atomic cursor so successive refreshes sweep the whole pool instead of
hammering the same few terms) and keep only <=90s-duration results as the
"is this a Short" signal, since none of the three backends expose a reliable
Shorts flag on search results. Even Innertube, which *does* have a genuine
signal (`reelItemRenderer` on the home feed's Shorts shelf), uses search as
its primary volume driver and the shelf only as a secondary top-up — the
shelf alone is one small fixed list, same problem as the other two.

`ShortsFeedRepository` queries all three sources in parallel each refresh,
de-dupes by video ID, interleaves them round-robin (one Innertube item, one
NewPipe item, one Invidious item, repeat), and caches the result to Room
(`ShortsCacheEntity`) so a total-failure refresh (no network, all three down)
falls back to the most recent cache instead of an empty feed.

Playback uses a **single shared ExoPlayer** (Media3) attached to whichever
`PlayerView` is the currently-visible `ViewPager2` page — the standard
TikTok/Shorts-feed pattern, not one player per row. Save routes through the
**existing** `DownloadService.startHttps(...)` — no second download path was
created. Like persists to `ShortsCacheEntity.isLiked` (a real DB write; a
proper `LikedEntity` doesn't exist until Phase 10, at which point the two
should be connected — noted in ARCHITECTURE.md Phase 10, don't let it stay
as two disconnected "liked" concepts).

## Honest limitations of this session's Phase 2 work

- **Not compiled or run.** No Android SDK was available in the sandbox this
  was written in — everything here is structurally correct against the real
  NewPipeExtractor/Media3/Invidious APIs (verified via web search where I
  wasn't already confident, not guessed), but **you must actually build this
  before trusting it further.** This is the single most important thing to
  do first in Session 6 — see "Immediate next steps."
- The <=90s duration heuristic will occasionally misclassify a short
  non-Shorts video as a Short and vice versa. There's no better signal
  available from any of the three backends for search-derived candidates —
  this is a real limitation of the approach, not a bug to "fix" by finding a
  cleverer regex.
- Innertube's `WEB_CLIENT_KEY`/`ANDROID_CLIENT_KEY`/`ANDROID_CLIENT_VERSION`
  constants (in `InnertubeShortsSource.kt`) are YouTube-internal values that
  drift over time. If Innertube starts returning empty pages or 403s, check
  these first — search "youtube innertube client version" for current values
  used by yt-dlp/NewPipe.
- `InvidiousShortsSource.DEFAULT_INSTANCES` is a hardcoded list of 4 public
  instances. Public instances come and go; if all 4 are down the source
  degrades to empty for that refresh (the repository's cache fallback covers
  this at the feed level, but it's still worth periodically refreshing this
  list against docs.invidious.io/instances). Phase 14 makes this
  user-configurable instead of hardcoded.
- `ShortsQueryFeeder`'s seed list is static, not a live trending-topics feed
  — there's no API for that without an official YouTube Data API key, which
  is explicitly out of scope for this project (Innertube/NewPipe/Invidious
  only, never the official API). Phase 14 also covers making this
  user-editable.

## Immediate next steps

1. **Clone fresh, confirm this session's commit(s) landed** (see top of this
   document). If `HANDOVER.md` isn't at the repo root and/or `ARCHITECTURE.md`
   doesn't show 20 phases, the patch hasn't been applied yet — stop and flag
   this, don't proceed as if Phase 2 is further along than it actually is.
2. **Build the project.** This is the first real build attempt since Session
   5's Phase 2 work — a clean `./gradlew assembleDebug` (or equivalent) has
   not happened yet. Expect to find and fix compile errors; none of this was
   verified against an actual Kotlin/Android toolchain. Prioritize this over
   starting Phase 3.
3. Once it builds, **run it and actually watch the Shorts tab scroll** for a
   couple minutes — confirm the three sources are really contributing (log
   or breakpoint on `ShortsFeedRepository.loadFeed()`'s per-source counts is
   the fastest way to see this), confirm playback doesn't leak players across
   swipes, confirm `loadMore()` actually appends instead of duplicating.
4. **Re-read `ARCHITECTURE.md` in full**, especially the new "Restructured
   from 8 phases to 20" note and Phase 2's full write-up. Treat it, not any
   prior session's summary (including this one), as the source of truth for
   what exists vs. what's genuinely new.
5. **Re-read `state.json` fresh** — same standing caution as every prior
   handover: don't trust any snapshot quoted in a document, including this
   one.
6. **Start Phase 3** (Home screen shell) once the build is confirmed clean —
   it's now intentionally small (just the screen + reading `pending_url`,
   no network) so it should be finishable in one session on its own.
7. **Carried over from sessions 1–4, still not done:**
   - `AGENTS.md` truncation warning (80,689 chars vs 30,720 limit) — check
     if it's losing important instructions.
   - Deprecated `.env` setting `TERMINAL_CWD` should move to `config.yaml`
     under `terminal: cwd:`.
   - Strip leftover `[debug]` console.log lines from
     `/root/fallback-router/server.js`.
   - `Termux:Boot` still doesn't fire `BOOT_COMPLETED` on this device
     (known OEM/MIUI restriction); `.bashrc` resume-on-open trigger remains
     the working mitigation.
   - Provider health: re-check `~/router.log` before assuming anything about
     which provider is carrying real work — this hasn't been re-verified
     since session 3.

## Standing verification habits (still true, still not automated)

After any cycle or manual session:
- `cat ~/.hermes-pipeline/state.json` — did it advance correctly, is
  `last_updated` a real timestamp, does `current_phase` actually match
  what `ARCHITECTURE.md`'s Definition of Done says for the current repo
  state (not just what the model claimed)?
- `git log --oneline -5` — real commit, message matches actual diff.
- `git diff` on anything unstaged — no surprise edits.
- Read the actual file content for anything claiming "no stubs" —
  `grep TODO` alone is not sufficient, as Session 4 found with
  `ShortsViewModel.kt`, and as Session 5 found with the missing-imports bug
  that `grep TODO` also wouldn't have caught (it's not a stub in the "fake
  data" sense — it's a straightforwardly broken file that nobody had tried
  to actually compile).
- Given this project's history (0/3 clean cycles before session 3's prompt
  rewrite, mixed since, and Session 5's Phase 2 work is unverified against a
  real compiler as of this handover), don't assume unattended reliability
  from any single clean run — keep watching a few more before trusting it
  unsupervised.
