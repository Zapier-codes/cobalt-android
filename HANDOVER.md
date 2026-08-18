# Cobalt-Android — Handover (Session 6 → 7)

**Start by cloning the repo fresh: `git clone https://github.com/Zapier-codes/cobalt-android.git`**
Do not trust any file content quoted in this document or prior handovers as
current — clone, then read the real files.

**This file itself should now be committed at the repo root as
`HANDOVER.md`.** If you don't find it there, this session's work hasn't
landed yet.

## Important: this repo is being worked by two independent actors

A background autonomous "Hermes Pipeline" cycle (`cycle-prompt.txt`) has
been pushing to `origin/master` at times concurrently with this chat
session — it once carried the project from Phase 5 to Phase 14 while this
session was doing other work, and separately collided with this session on
Phase 5 itself (see the Session 6 git history for how that was resolved).
**Always `git fetch` and re-read `ARCHITECTURE.md` immediately before
starting anything, even mid-session.**

## What Session 6 did, most recent work first

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
Expect (top of log): "Mark Phase 16 done in ARCHITECTURE.md", the
fragment/banner commit, the ViewModel commit, the repository/`FeedPage`
commit, then the PeerTube-source and Phase 15 commits from earlier this
session, then `5fc8cef` (Phase 14). If Phase 16 isn't at the tip, don't
assume it landed — and check commit authorship for any unexpected `Hermes
Pipeline` commits, which would mean the pipeline moved the repo further
while this was being built.

## Honest limitations of this session's Phase 16 work

- **Not compiled or run.** True of every phase since before Session 5.
  With Phase 16 landing, that's now 16 phases plus the PeerTube addendum
  entirely unverified against a real Kotlin/Android compiler — see
  "Immediate next steps," this is genuinely overdue now, not a standing
  disclaimer to skim past.
- The cache-fallback banner is purely informational this phase — no
  dismiss action, no manual retry button. Reasonable future polish, not
  required by Phase 16's DoD.
- `isOffline` reflects the last fetch attempt's outcome, not a continuous
  connectivity listener — matches the DoD's own framing (what a fetch
  attempt surfaces), but worth knowing if it comes up as a "why didn't the
  banner show immediately" question later.

## Immediate next steps

1. **`git fetch` and re-read `ARCHITECTURE.md` before anything else** —
   don't trust this handover's phase count against a pipeline that may
   have moved since, and don't assume the AI-generation feature request
   is dead just because it's not in the numbered phases — it may come up
   again.
2. **Build the project.** This is now the clear highest-priority action —
   16 phases plus the PeerTube addendum, zero real-compiler verification.
   Consider making this the mandatory first step of any session (manual
   or pipeline) before writing more code on an unknown compile state.
3. **Continue with Phase 17** (loading placeholders on Home/Shorts/
   Downloads) once a build is confirmed clean.
4. **If the AI-generated-content feature comes up again**, see "Also
   declined this session" above before doing anything — the mitigated
   version (separate labeled section, fiction-only, no blending) is fine
   to build; the unlabeled/blended/unattended version is not, regardless
   of how the request is framed.
5. **Carried over from sessions 1-5, still not done** (status vs. pipeline
   work unverified — check before assuming): `AGENTS.md` truncation
   warning, `.env`'s deprecated `TERMINAL_CWD`, leftover `[debug]` logs in
   `/root/fallback-router/server.js`, `Termux:Boot` not firing
   `BOOT_COMPLETED`, `~/router.log` provider-health re-check.

## Standing verification habits

- `git fetch` + `git log --oneline -10` on `origin/master` — check commit
  authorship; an unexpected `Hermes Pipeline` commit means it's running
  concurrently, not a bug.
- Read `ARCHITECTURE.md`'s actual `✅ done` markers, not a prior handover's
  phase count.
- Take "verify X works" DoD items literally, not as a formality — two
  separate verification steps this session (Phase 15's query-cursor
  exhaustion, Phase 16's duplicate History writes) surfaced real bugs that
  a superficial "yes it's wired up" check would have missed.
- Given 16 phases plus the PeerTube addendum with zero real-compiler
  verification, a confirmed clean build is overdue, not optional.
