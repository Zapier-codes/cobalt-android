# Cobalt-Android — Handover (Session 6 → 7)

**Start by cloning the repo fresh: `git clone https://github.com/Zapier-codes/cobalt-android.git`**
Do not trust any file content quoted in this document or prior handovers as
current — clone, then read the real files.

**This file itself should now be committed at the repo root as
`HANDOVER.md`.** If you don't find it there, this session's work hasn't
landed yet.

## Important: this repo is being worked by two independent actors

A background autonomous "Hermes Pipeline" cycle (`cycle-prompt.txt`) has
been running and pushing to `origin/master` concurrently with this chat
session. It carried the project from Phase 5 to Phase 14 while this
session was doing other work. **Always `git fetch` and re-read
`ARCHITECTURE.md` immediately before starting anything, even mid-session.**

## What Session 6 did, most recent work first

### New Shorts source: PeerTube (outside the numbered phase sequence)

Per explicit user request: "add any Shorts providers with a free API or
public instances you can find, deep search." Findings, all backed by real
research this session (see below for exactly what was checked):

- **Added `PeerTubeShortsSource`** — commits `f040ce4` (added `PEERTUBE` to
  `ShortsSourceType`) and `290478b` (the source itself + wired into
  `ShortsFeedRepository`). PeerTube is open-source, federated
  (ActivityPub), genuinely free/open the same way Invidious and
  NewPipeExtractor already are here.
  - **Discovery**: `GET https://sepiasearch.org/api/v1/search/videos?search=<query>`
    — Framasoft's federated search index covering ~800+ public PeerTube
    instances at once, no auth. **Verified live this session**: fetched
    `.../search/videos?search=news&count=3` and got real, current (Aug
    2026) results with the expected shape (`data[].uuid`, `.duration`,
    `.channel.host`, `.thumbnailUrl`, etc).
  - **Resolution**: `GET https://{origin-instance}/api/v1/videos/{uuid}`
    (each result's own host, not sepiasearch itself) returns `files[]`
    (progressive MP4 per resolution) and/or `streamingPlaylists[].files[]`
    (HLS — `fileUrl` ends in `-fragmented.mp4`; swap that suffix for
    `.m3u8` to get the real playlist URL, since PeerTube's API doesn't
    publish that URL directly — confirmed via
    github.com/Chocobozzz/PeerTube/issues/6615, a still-open feature
    request asking for exactly that). **Not verified live** — this
    specific per-instance endpoint wasn't fetchable from this sandbox
    (only URLs already surfaced by a prior search/fetch can be fetched
    here, and no search surfaced a live example response body for this
    exact endpoint) — the shape is taken from PeerTube's own GitHub
    issues/docs, not guessed, but confirm it against a real instance
    before trusting it fully.
  - Same <=90s duration filter the other three sources use, applied to
    SepiaSearch's `duration` field before any per-video resolve call
    (PeerTube hosts general-length video, not Shorts-specific content).
  - `videoId` is prefixed `"peertube:<uuid>"` to guarantee no collision
    with the other three sources' 11-char YouTube IDs in the shared
    dedup/cache/history/liked tables, all keyed by `videoId`.
  - No hardcoded/configurable instance list needed (unlike
    `InvidiousShortsSource`) — one federated index covers the whole
    network.

- **Considered, not added: Loops** (`loops.video`, Pixelfed's open-source
  federated TikTok alternative). Fits the same "genuinely open/free" bar
  as PeerTube in principle, but is still in public beta and no documented
  public API was found this session (unlike PeerTube's long-stable,
  documented REST API). Worth revisiting later if its API surface matures
  — don't add it on a guessed/undocumented API shape.

- **Explicitly declined: "DramaWave", "ReelShort", "DramaBox", and similar
  short-drama apps**, which the user named directly. These are commercial,
  paywalled (coins/ads/VIP), copyrighted entertainment platforms produced
  by real publishers (STORYMATRIX, NewLeaf Publishing, GoodNovel, etc.) —
  not free/open in the sense the user's own stated criterion asked for.
  The only "APIs" found for them are unofficial third-party resellers
  (a WJunction forum ad, a "DramaBos" service) that reverse-engineer these
  apps' private backends and resell access via Telegram bots — not a
  legitimate public API under any reading of what was asked for, and this
  session did not integrate them. Documented in `ARCHITECTURE.md` so this
  doesn't get silently re-attempted or mistaken for an oversight later —
  if the user wants this revisited, it needs its own explicit
  conversation, since "pull paywalled commercial content via an
  unauthorized reseller API" is a materially different request from
  everything else in this file.

Full reasoning and citations are in `ARCHITECTURE.md`'s Phase 2 addendum.

### Phase 5 collision + Phase 15 (earlier this session)

1. Built Phase 4 (`LinkResolverRepository`) and Phase 5
   (`ResolutionCacheEntity`/`Dao`) manually.
2. **Discovered a Phase 5 collision**: the Hermes Pipeline had
   independently pushed its own, lower-quality Phase 5 (commits `ae452dd`,
   `cc36004`) at the same time — different path (`db/entities/` vs this
   session's `download/`), single-URL storage (loses picker-status
   multi-format resolves), non-suspend Dao, and a `DownloadDatabase.kt`
   that didn't compile (`Context` used, never imported). Started
   reconciling it manually, then re-fetching showed **the pipeline had
   already resolved the whole collision itself** and moved on through
   Phase 14 — abandoned the in-progress manual reconciliation in favor of
   the pipeline's already-correct, already-pushed resolution (verified:
   spec's `db.entities`/`db` path, full `formatsJson` list, real
   `Migration(1,2)`, version 3 with Phase 9's History/Liked sharing the
   same database).
3. Built **Phase 15** (Shorts feed hardening) from that verified HEAD —
   commits `ec06a33` (backoff + timeout tuning), `2451855` (marked done):
   - De-dupe logic verified correct by code trace (not a live run).
   - **Real finding**: `ShortsQueryFeeder`'s rotation cursor is shared
     across all 3 (now 4) sources, not per-source — the query pool
     exhausts after ~3-4 `loadMore()` calls, not the ~10 a naive reading
     would suggest. Documented, not fixed (out of this phase's scope).
   - Added per-`ShortsSourceType` exponential backoff (30s→...→15min cap).
   - Retuned `SOURCE_TIMEOUT_MS` 12s→20s and `InvidiousShortsSource`'s
     per-instance timeouts 10/15s→6/8s, reasoned from that source's
     sequential 4-instance failover design.

## Verify this landed

```
cd cobalt-android
git fetch origin
git log --oneline origin/master -8
```
Expect (top of log): "Document PeerTube source addition...", the
PeerTube-source commit, the `PEERTUBE` enum commit, "Mark Phase 15 done...",
the backoff/timeout commit, then whatever the Hermes Pipeline's tip was
when this session started (`5fc8cef`, Phase 14 — **check it's not stale**,
the pipeline may have moved further since).

## Honest limitations of this session's work

- **Nothing has been compiled or run.** True of every phase since before
  Session 5. With ~15 phases plus this addendum now unverified against a
  real compiler, this is overdue — see "Immediate next steps".
- **PeerTubeShortsSource's per-video resolve endpoint
  (`/api/v1/videos/{uuid}`) was not fetched live this session** — its
  shape is taken from PeerTube's own GitHub issues/docs (specifically the
  `-fragmented.mp4` → `.m3u8` HLS workaround, confirmed via a real, open
  GitHub issue discussing exactly that gap), not guessed, but not
  round-tripped against a real response either. The SepiaSearch discovery
  call *was* verified live. Confirm the resolve step against a real
  instance before trusting it fully.
- Query-pool exhaustion (Phase 15 finding) still applies, and now spans
  one more source competing for the same shared cursor.
- `PeerTubeShortsSource` has no per-request backoff exemption tuning of
  its own yet (it inherits Phase 15's generic per-`ShortsSourceType`
  backoff automatically, which should be sufficient, but wasn't
  specifically re-verified against this new source's failure modes).

## Immediate next steps

1. **`git fetch` and re-read `ARCHITECTURE.md` before anything else** —
   don't trust this handover's phase/addendum count against a pipeline
   that may have moved since.
2. **Build the project.** Now clearly the single highest-value next
   action — nothing in Phases 2-15 plus this addendum has touched a real
   Kotlin/Android compiler.
3. **Verify `PeerTubeShortsSource` against a real instance** — confirm a
   `/api/v1/videos/{uuid}` response actually has the `files`/
   `streamingPlaylists` shape assumed, and that the `.m3u8` suffix swap
   produces a playable URL.
4. **If the user raises the drama-app sources again**, don't silently
   integrate them — that conversation needs to happen explicitly given
   what those "APIs" actually are (see the addendum above and
   `ARCHITECTURE.md`).
5. **Continue with Phase 16** (Shorts feed polish) once the build is
   confirmed clean.
6. **Carried over from sessions 1-5, still not done** (status vs. pipeline
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
- Read actual file content for "no stubs" claims — `grep TODO` alone has
  missed real bugs multiple times across sessions (imports, compile
  errors, quality gaps).
- Given ~15 phases plus this addendum with zero real-compiler
  verification, and two independent actors having already collided once
  on the same phase — a confirmed clean build is overdue, not optional.
