# Cobalt-Android — Handover (Session 6 → 7)

**Start by cloning the repo fresh: `git clone https://github.com/Zapier-codes/cobalt-android.git`**
Do not trust any file content quoted in this document or prior handovers as
current — clone, then read the real files. This document explains what
changed and why, not what's guaranteed to still be true by the time you
read it.

**This file itself should now be committed at the repo root as
`HANDOVER.md`.** If you don't find it there, this session's work hasn't
landed yet — stop and flag that before assuming any of it is live.

## Important: this repo is being worked by two independent actors

During this session, a **background autonomous "Hermes Pipeline" cycle**
(commit author `Hermes Pipeline <hermes@localhost>`, driven by
`cycle-prompt.txt`) was running and pushing to `origin/master`
**concurrently** with this chat session doing manual work on the same
repo. This caused a real collision on Phase 5 (see below) and, separately,
the pipeline autonomously carried the project from Phase 5 all the way
through **Phase 14** while this session was mid-Phase-5 — several phases
ahead of what this session was tracking.

**Practical implication for Session 7 (and beyond): always `git fetch` and
read `ARCHITECTURE.md` fresh from `origin/master` immediately before
starting any work, even mid-session, not just at session start.** The repo
can and does move out from under a session that isn't actively watching
for it. If you're a manual chat session like this one, assume the pipeline
may be running at the same time and re-check before every phase, not just
once.

## What Session 6 actually did, in order

1. **Phase 4** (real `LinkResolverRepository`) and **Phase 5**
   (`ResolutionCacheEntity`/`Dao`) — built manually, patches handed to the
   user to `git am` + push by hand (this repo's human collaborator applies
   patches from a phone; there is no direct push access from this sandbox).
2. **Phase 5 collision discovered.** The user's `git am` of this session's
   Phase 5 patch partially failed: `DownloadDatabase.kt` didn't match the
   patch's expected base content. Re-cloning `origin/master` fresh showed
   why — the Hermes Pipeline had independently pushed its *own* Phase 5
   implementation (commits `ae452dd`, `cc36004`) while this session was
   still working, at `db/entities/ResolutionCacheEntity.kt` +
   `db/ResolutionCacheDao.kt` (the spec's originally-named path). Its
   version was lower quality than this session's: single `resolvedUrl`+
   `title` fields (loses all but one format for any `picker`-status
   resolve — doesn't match what `LinkResolverRepository.ResolveResult.
   Success.formats: List<ResolvedFormat>` actually produces), non-suspend
   Dao methods, and it left `DownloadDatabase.kt` **not compiling** (added
   a `Context` usage without importing `android.content.Context`). This
   session started reconciling it (removing the duplicate, fixing the
   import) directly against `origin/master`, then was asked to "pull the
   latest commit and continue from there" — re-fetching at that point
   showed the **Hermes Pipeline had already resolved the whole collision
   itself** (see next point) and moved on through Phase 14. This session's
   in-progress reconciliation was abandoned in favor of the pipeline's
   already-landed, already-pushed resolution — redoing it would have been
   redundant and risked a second collision.
3. **Confirmed the pipeline's Phase 5 resolution is sound.** Read the
   actual current `DownloadDatabase.kt`/`ResolutionCacheEntity.kt`/
   `ResolutionCacheDao.kt` on `origin/master`: consolidated at the spec's
   `db.entities`/`db` path, full `formatsJson` list (not single-URL), real
   `Migration(1,2)` (not `fallbackToDestructiveMigration`), version bumped
   to 3 (History/Liked, Phase 9, share this database too — see
   `DownloadDatabase.kt`'s own doc comment). No leftover duplicate files.
   `LinkResolverRepository` reads/writes through it with the same
   5-minute-freshness reasoning this session had already independently
   arrived at. Did not re-do or second-guess this — it's correct and
   already live.
4. **Verified Phases 1-14 are genuinely at HEAD** via `ARCHITECTURE.md`'s
   own per-phase `✅ done` markers and `git log`, rather than assuming from
   the phase-count jump alone.
5. **Built Phase 15** (Shorts feed hardening) manually from that verified
   HEAD — pushed as commits `ec06a33` (backoff + timeout tuning) and
   `2451855` (ARCHITECTURE.md marked done):
   - **DoD-1 (verify pagination de-dupe)**: verified by code trace, not a
     live run (no network egress to YouTube/Invidious/NewPipe from this
     sandbox). De-dupe logic itself (`ShortsFeedRepository.dedupeById` +
     `ShortsViewModel.loadMore()`'s existing-ID filter) is structurally
     correct at every layer — no bug found there. **Did** find a real,
     previously-undocumented limitation: `ShortsQueryFeeder`'s rotation
     cursor is a single `object`-level `AtomicInteger` shared across *all
     three* sources, not per-source — each `loadFeed()` call advances it
     by 9 (3 sources × `QUERIES_PER_FETCH=3`), not 3, so the 30-term query
     pool starts repeating after roughly **3-4 `loadMore()` calls**, not
     the ~10 a naive per-source reading would suggest. Documented in
     `ARCHITECTURE.md`'s Phase 15 write-up and flagged as a real UX
     ceiling for a future phase to address (larger/expandable query pool,
     or genuine per-query result pagination) — not fixed this phase,
     which was scoped to pagination *correctness* + backoff, not query
     diversity depth.
   - **DoD-2 (backoff)**: added `ShortsFeedRepository.
     fetchFromSourceWithBackoff` — per-`ShortsSourceType` exponential
     backoff (30s/60s/120s/... doubling per consecutive timeout-or-
     exception, capped 15 min, cleared on next real response). An empty
     non-exceptional result is treated as "reachable, nothing new," not a
     failure — all three sources' aggressive filtering can legitimately
     yield zero results for a healthy source.
   - **DoD-3 (timeout tuning)**: `SOURCE_TIMEOUT_MS` 12s -> 20s;
     `InvidiousShortsSource`'s per-request `connectTimeout`/`readTimeout`
     10s/15s -> 6s/8s. Reasoning: that source fails over across up to 4
     instances *sequentially* per query (up to 3 queries/fetch) — a single
     slow instance at old timeouts could burn most of the *old* 12s outer
     budget before failover ever reached a healthy instance. Shorter
     per-instance timeouts + a larger outer budget together give real
     failover an actual chance to complete. Reasoned, not measured live.
6. **Did not touch Phase 16 or beyond** — next open phase per
   `ARCHITECTURE.md` as of this handover.

## Verify this landed

```
cd cobalt-android
git fetch origin
git log --oneline origin/master -6
```
Expect (top of log): "Mark Phase 15 done in ARCHITECTURE.md", the backoff/
timeout-tuning commit, then whatever the Hermes Pipeline's Phase 14 tip
was when this session started (`5fc8cef` at the time this session pulled
— **check it's not stale**, the pipeline may well have moved further by
the time you read this). If Phase 15 isn't at the tip, don't assume it
landed.

## Honest limitations of this session's Phase 15 work

- **Not compiled or run, and not verified against real Invidious/
  Innertube/NewPipe traffic.** Same standing sandbox limitation as every
  phase this session has touched. The backoff schedule and retuned
  timeouts are reasoned from the existing code's own structure (instance
  counts, query counts, old timeout values), not measured against live
  services.
- The query-pool-exhaustion finding (DoD-1) is documented, not fixed.
  Flagging again here so it doesn't get lost: `ShortsQueryFeeder`'s shared
  cursor across all 3 sources means meaningful new-content depth via
  `loadMore()` is much shallower (~3-4 pages) than the pool size alone
  would suggest. Worth a dedicated phase if users report the Shorts feed
  "running dry" quickly.
- Backoff state is in-memory only, scoped to one `ShortsFeedRepository`
  instance's lifetime — resets on process death. Considered acceptable,
  not a gap to close.

## Immediate next steps

1. **`git fetch` and re-read `ARCHITECTURE.md` before doing anything else**
   — per "Important: this repo is being worked by two independent actors"
   above, don't trust this handover's phase count.
2. **Build the project.** Still true as of this handover: no clean
   `./gradlew assembleDebug` has been confirmed since before Session 5.
   With the codebase now at ~15 phases of unverified-against-a-compiler
   work, this is increasingly the highest-value single action available —
   consider making it the *first* thing any session does, manual or
   pipeline, before adding more code on top of an unknown compile state.
3. **Test against real services** once compiling: a real cobalt instance
   (Phase 4/5), and real Innertube/NewPipe/Invidious traffic (Phase 15's
   backoff/timeout tuning, and the query-exhaustion finding above).
4. **Consider whether the query-pool-exhaustion finding warrants its own
   phase** — it's real, documented, and not addressed by Phases 15 or 16
   as currently scoped.
5. **Continue with Phase 16** (Shorts feed polish: offline indicator +
   confirming History writes fire from real playback) once Phase 15 is
   confirmed landed and, ideally, the project has been compiled at least
   once.
6. **Carried over from sessions 1-5, still not done** (unverified whether
   the pipeline has addressed any of these — check before assuming):
   - `AGENTS.md` truncation warning (80,689 chars vs 30,720 limit).
   - Deprecated `.env` setting `TERMINAL_CWD` should move to
     `config.yaml` under `terminal: cwd:`.
   - Strip leftover `[debug]` console.log lines from
     `/root/fallback-router/server.js`.
   - `Termux:Boot` doesn't fire `BOOT_COMPLETED` on this device; `.bashrc`
     resume-on-open trigger is the working mitigation.
   - Provider health: re-check `~/router.log` before assuming anything
     about which provider is carrying real work.

## Standing verification habits (still true, still not automated)

- `git fetch` + `git log --oneline -10` on `origin/master` — a real
  commit, message matches actual diff, **and check the author** — a
  `Hermes Pipeline` commit you didn't expect is a signal the pipeline is
  running concurrently, not a bug.
- Read `ARCHITECTURE.md`'s actual per-phase `✅ done` markers, not a prior
  handover's phase count, as the source of truth for what's actually
  complete.
- Read the actual file content for anything claiming "no stubs" — `grep
  TODO` alone is not sufficient (Session 4's `ShortsViewModel.kt`,
  Session 5's missing-imports bug, and this session's own
  `DownloadDatabase.kt` missing-`Context`-import discovery all would have
  been missed by a TODO grep).
- Given this project's history (0/3 clean cycles before session 3's
  prompt rewrite, mixed since, ~15 phases of code now unverified against
  a real compiler, and this session's own discovery of two independent
  actors racing on the same phase) — don't assume unattended reliability.
  Getting a clean build confirmed is now overdue, not optional polish.
