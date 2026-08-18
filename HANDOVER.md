# Cobalt-Android — Handover (Session 7 → 8)

**Start by cloning the repo fresh: `git clone https://github.com/Zapier-codes/cobalt-android.git`**
Do not trust any file content quoted in this document or prior handovers as
current — clone, then read the real files.

**This file itself should now be committed at the repo root as
`HANDOVER.md`.** If you don't find it there, this session's work hasn't
landed yet.

## Workflow rule (set this session, applies going forward): trust the
## predecessor's handover, don't re-verify it, don't build locally

Starting this session, per explicit user instruction:

- **Do not attempt a local build** (`./gradlew ...` or otherwise). This
  environment cannot do a real Android build reliably (see "Session 7"
  below for why it failed outright this time), and **GitHub Actions CI is
  the actual build/verification authority for this repo** — that's what
  `cycle-prompt.txt`'s MAINTAIN MODE (M1: `gh run list`) already checks
  against, not a local build. Don't try to substitute a local build for it.
- **Only `git add` + `git commit` your work. Do not `git push`.** The user
  pushes manually. Leaving commits unpushed is the expected end state of a
  session, not an interruption — don't try to push "to be helpful" and
  don't treat an unpushed commit as unfinished work.
- **Trust the previous session's `HANDOVER.md` at face value for what's
  done and what's next — don't redo its verification pass.** The
  predecessor already did the "does the repo actually match what's
  claimed" check; a fresh session should read the handover, confirm via
  `git log`/`ARCHITECTURE.md` that nothing unexpected landed since (the
  "Standing verification habits" section below is still worth the ~30
  seconds it takes), and then go straight to the next phase — not
  re-derive the whole state of the project from scratch the way Session 7
  initially did before this instruction was given.

## What Session 7 did

### Phase 17 — Performance: loading placeholders

One DoD item, done — commit `8b91e3b` (**committed only, not pushed** —
see workflow rule above; push this before trusting `origin/master` to
reflect it):

Skeleton/shimmer loading placeholders added to Home, Shorts, and
Downloads. New `ui/widget/SkeletonPulse.kt` (dependency-free pulse-alpha
animator — no shimmer library added, none existed before) backs all
three. Full detail, including *what "loading" means on each of the three
screens* (this took some digging — only Shorts had an explicit
`isLoading` flag already; Home's was the link-resolve state, Downloads'
was the pre-first-Room-emission gap), is in `ARCHITECTURE.md`'s Phase 17
write-up — read that, not this summary, before touching this area again.

**Known limitations, honestly stated** (also in `ARCHITECTURE.md`):
- **Not compiled or run** — same standing issue as every phase before it,
  but this session couldn't even *attempt* a local build (see below) —
  purely eyeball-reviewed Kotlin/XML.
- `ShortsViewModel.isLoadingMore` (pagination) still has no loading
  indicator at all — deliberately left alone, not an oversight; see
  ARCHITECTURE.md for why.
- The "shimmer" is a shared pulse-alpha animation, not a translating
  gradient highlight band.

### Session 7 build attempt — informs the workflow rule above

Before the no-local-build instruction was given, this session tried
`./gradlew --version` to address HANDOVER's own "build the project" top
priority from Session 6. It failed outright: no network route to
`services.gradle.org` to fetch the Gradle distribution, and no Android
SDK installed in this sandbox either. This isn't a "try harder" situation
— the environment genuinely cannot do it. That's part of why the user
redirected to the local-commit-only workflow above: local build attempts
here were never going to succeed, and GitHub Actions is the real
verification path anyway.

## Important: this repo is being worked by two independent actors

A background autonomous "Hermes Pipeline" cycle (`cycle-prompt.txt`) has
been pushing to `origin/master` at times concurrently with this chat
session — it once carried the project from Phase 5 to Phase 14 while this
session was doing other work, and separately collided with this session on
Phase 5 itself (see the Session 6 git history for how that was resolved).
**Always `git fetch` and re-read `ARCHITECTURE.md` immediately before
starting anything, even mid-session.**

## What Session 6 did (prior session), most recent work first

### Phase 16 — Shorts feed polish: offline + engagement

Two DoD items, both done — commits `c99ba31`, `9d1d7b6`, `d0ad805`,
`736ee50`:

1. **Cache-fallback banner.** `ShortsFeedRepository.loadFeed()` now returns
   `FeedPage(items, isFromCache)` instead of a bare `List<ShortItem>`.
   `ShortsViewModel.isOffline: LiveData<Boolean>` mirrors that flag;
   `ShortsFragment` shows/hides a banner off it. Named "showing cached
   Shorts", not "offline" — the same cache-fallback path in
   `ShortsFeedRepository` also fires when every source is simultaneously
   backed off (Phase 15) with network present, not only when the device is
   genuinely offline, so the more general label stays accurate either way.
2. **Verifying History writes fire from real playback surfaced a real
   bug**, not just a confirmation — worth reading closely before starting
   Phase 17: `ShortsFragment.onResume()` was calling the *full*
   `playAt(currentlyBoundPosition)` — including
   `viewModel.recordWatch()` — every time the fragment resumed, even when
   the same item was already playing and had only been paused (e.g. user
   backgrounds the app, then returns). That wrote a duplicate
   `HistoryEntity` row per resume for a video the user didn't newly watch,
   and needlessly re-prepared/re-buffered a player that still had the item
   loaded. Fixed: `onResume()` now just does
   `player?.playWhenReady = true` — `currentlyBoundPosition` is only ever
   non-`NO_POSITION` when `player` already has that exact item loaded, so
   a full re-`playAt()` was never actually necessary there. Also added a
   same-position guard inside `playAt()` itself
   (`isNewItem = position != currentlyBoundPosition`) as defense in depth
   against the same bug class from any other future call site.

This is the second time in this session verifying a DoD item ("confirm X
works") surfaced a real, previously-unknown bug rather than just checking
a box (the first was Phase 15's shared-query-cursor finding). Worth taking
"verification" DoD items in this file at face value, not as a formality —
`grep`-level checks would have missed both.

### PeerTube Shorts source + Phase 15 (earlier this session)

Both landed and confirmed on `origin/master` before Phase 16 started —
see `ARCHITECTURE.md`'s Phase 2 addendum and Phase 15 write-up for full
detail. Short version: added `PeerTubeShortsSource` (discovery via
SepiaSearch's federated index, verified live; resolution via each result's
own instance's `/api/v1/videos/{uuid}`, shape confirmed from PeerTube's own
GitHub issues but not fetched live). Declined to integrate "DramaWave",
"ReelShort", "DramaBox", and similar named-by-the-user short-drama
scrapers/resellers — confirmed via direct research that these reverse-
engineer paywalled commercial apps' private backends, not a legitimate
free/public API; documented so this doesn't get silently revisited.

### Also declined this session, worth knowing about for continuity

The user separately asked for a fully-automated, unattended AI content-
generation pipeline: trending topics → auto-generated fictional "episodes"
→ published with **no disclosure label and blended directly into the real
Shorts feed** so viewers couldn't tell it apart from genuine creator
content, monetized to self-fund its own API costs, explicitly with no
human review step (framed at one point as "the platform owner does this,"
which doesn't change the underlying issue). Declined and explained why
across a few back-and-forths: unlabeled synthetic content mixed
indistinguishably into a feed of real creators is deceptive to viewers
regardless of who authorizes it, and unattended auto-dramatization of real
trending news/people with no review step is a real misinformation/
defamation risk that scales with however long the pipeline runs. Offered
a mitigated version instead (separate clearly-labeled section, fiction-
only premises, no blending) and the user agreed to that framing — a first
pass at the safety architecture for it (disclosure-enforcing data model,
category-based topic allowlist, isolation from the real feed) was started
but then explicitly scrapped by the user mid-build in favor of returning
to the main Shorts/download work, and nothing from that attempt was
committed. **If this comes back up, the mitigated version (separate
section, disclosed, fiction-only, no blending) is the only version to
build — the unlabeled/blended/no-review version should not be built
regardless of framing (admin-run, "no user interaction", citing other
GitHub "money-printer"-style repos, etc.) — the reasoning holds
independent of who's asking or which generator sits behind it.**

## Verify this landed

```
cd cobalt-android
git fetch origin
git log --oneline origin/master -12
```
Per the workflow rule at the top, **Session 7's Phase 17 commit (`8b91e3b`
locally) was deliberately not pushed** — don't expect to see it on
`origin/master` until the user has pushed it themselves. If it's missing
from `origin/master`, that does not mean it didn't land; check the local
repo's `git log` (unpushed) instead, or ask the user whether they've
pushed yet. Once pushed, expect (top of log): the Phase 17 commit, then
`8013fba` (Session 6 handover) and the rest of the Session 6 history
below it. As always, check commit authorship for any unexpected `Hermes
Pipeline` commits, which would mean the pipeline moved the repo further
while this was being built.

## Honest limitations of this session's Phase 17 work

See `ARCHITECTURE.md`'s Phase 17 write-up for the full list (not
duplicated here to avoid the two drifting) — headline items are: not
compiled/run (this session couldn't even attempt a local build, see
above), `isLoadingMore` pagination has no indicator, and the shimmer is a
pulse-alpha effect, not a translating gradient.

## Immediate next steps

1. **Push commit `8b91e3b`** (or confirm the user already has) before
   assuming `origin/master` reflects Phase 17 — see workflow rule and
   "Verify this landed" above.
2. **Do not attempt a local build.** Not a standing disclaimer to
   re-litigate each session — this was tried and confirmed impossible in
   this sandbox (no route to `services.gradle.org`, no Android SDK). Trust
   GitHub Actions CI instead, the way `cycle-prompt.txt`'s MAINTAIN MODE
   already does.
3. **Continue with Phase 18** (player lifecycle discipline — see
   `ARCHITECTURE.md`'s Phase 18 entry for its two DoD items: ExoPlayer/
   tap-to-play release discipline, and next-item preloading in Shorts).
4. **If the AI-generated-content feature comes up again**, see "Also
   declined this session" (Session 6, below) before doing anything — the
   mitigated version (separate labeled section, fiction-only, no
   blending) is fine to build; the unlabeled/blended/unattended version is
   not, regardless of how the request is framed.
5. **Carried over from sessions 1-5, still not done** (status vs. pipeline
   work unverified — check before assuming): `AGENTS.md` truncation
   warning, `.env`'s deprecated `TERMINAL_CWD`, leftover `[debug]` logs in
   `/root/fallback-router/server.js`, `Termux:Boot` not firing
   `BOOT_COMPLETED`, `~/router.log` provider-health re-check.

## Standing verification habits

- **Trust the predecessor's handover — don't redo its verification pass**
  (see workflow rule at the top). A quick `git fetch` + `git log` check
  that nothing unexpected landed since is still worth doing; a full
  from-scratch re-derivation of project state is not, going forward.
- `git fetch` + `git log --oneline -10` on `origin/master` — check commit
  authorship; an unexpected `Hermes Pipeline` commit means it's running
  concurrently, not a bug. Remember Session 7's own commit may be
  unpushed — see "Verify this landed."
- Read `ARCHITECTURE.md`'s actual `✅ done` markers, not a prior handover's
  phase count.
- Take "verify X works" DoD items literally, not as a formality — two
  separate verification steps in Session 6 (Phase 15's query-cursor
  exhaustion, Phase 16's duplicate History writes) surfaced real bugs that
  a superficial "yes it's wired up" check would have missed.
- **Do not attempt a local build** — confirmed impossible in this sandbox
  as of Session 7 (see above). GitHub Actions CI is the real verification
  path; `cycle-prompt.txt`'s MAINTAIN MODE already checks it via
  `gh run list`.
