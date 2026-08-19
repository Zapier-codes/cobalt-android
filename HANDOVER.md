# Cobalt-Android — Handover (Session 10 → 11, this session)

**Start by cloning the repo fresh: `git clone https://github.com/Zapier-codes/cobalt-android.git`**
Do not trust any file content quoted in this document or prior handovers as
current — clone, then read the real files.

**This file itself should now be committed at the repo root as
`HANDOVER.md`.** If you don't find it there, this session's work hasn't
landed yet.

## Workflow, unchanged from Session 9/10: don't build locally,
## commit-only + patch handoff

- **Do not attempt a local build.** No route to `services.gradle.org`, no
  Android SDK in this sandbox. GitHub Actions CI (`build.yml`) is the real
  build authority.
- **Only `git add` + `git commit`. Do not `git push`.** Generate patch
  files and present them — the user applies via `git am` on their own
  device and pushes themselves.
- **"Hermes" the autonomous background loop is gone** (per Session 10) —
  only manual chat sessions move this project forward now. `git fetch`
  before starting anything, same as always, since parallel manual sessions
  are still possible.

## Continuation this session: CI's first real build run found two failures,
## both now fixed (patch `0008`)

This is a same-session continuation of the remote-config work below —
after that landed, the user pasted a live GitHub Actions failure log
(job "Fix CI build failure: activity_main.xml..." / run #37). Investigated
and found the log was from a run with **two** AAPT failures, not one:

1. `activity_main.xml`'s `tools:layout` without `xmlns:tools` — already
   fixed in `ad475ef` (already on `origin/master`, authored by a
   different actor, `Verify Sandbox <verify@sandbox.local>` — not this
   chat session; presumably another concurrent fixing pass, worth noting
   in case it indicates another background/CI-triggered fixer running
   against this repo now that Hermes itself is gone).
2. **Still broken after `ad475ef`**: `fragment_settings.xml` (Phase 13)
   used a `<layout>` (data-binding) root tag, but the project only has
   `viewBinding` enabled, never `dataBinding`. This is what the pasted
   log's actual error text was about — read closely, it was not the
   `activity_main.xml` error repeated. Fixed by unwrapping `<layout>`
   (verified `SettingsFragment.kt` uses plain ViewBinding with no
   data-binding-only features, so nothing depends on it — full reasoning
   in `ARCHITECTURE.md`'s new "CI build failures" section, read that
   before touching this file again). Diffed with `git diff -b -w` to
   confirm the change is wrapper-only, zero content/behavior change.

Both commits (`ec282e1`, `4d841fc`) are local-only, per the workflow
above. Patch `0008` (`git format-patch origin/master..HEAD`) covers both;
verified via `git am` against a **fresh fetch of the real, live
`origin/master`** (not just this sandbox's possibly-stale local copy —
see "Standing verification habits" below), and the resulting
`fragment_settings.xml` was confirmed to parse as well-formed XML. Number
`0008` is a best guess, not a confirmed continuation of a prior count —
this sandbox's `/mnt/user-data/outputs` was empty at the start of this
turn (fresh sandbox instance), so the actual last-used number from
Session 10/11's earlier patches couldn't be cross-checked here; rename if
it collides with something already downloaded.

**Not yet done:** an actual green GitHub Actions run confirming both
fixes together — this sandbox can watch neither `gh run list` nor trigger
a re-run. That confirmation is the first thing to check next session.

**Also carried in this sandbox, not yet delivered:** a substantial
FFmpeg-based dynamic quality/format transcoding feature (video ladder
2160p→360p + VP9, audio ladder FLAC/MP3-320/AAC/Opus/Vorbis, a real
`QualitySelectionSheet` bottom sheet, `TranscodeWorker` via WorkManager,
etc. — full file list would go here once it lands) was built earlier in
this sandbox's session against an older base commit (`85644bc`), then
against `85644bc`'s successor, and is now stashed twice
(`git stash list`) rather than committed, because `origin/master` moved
forward substantially (through Phase 19, the remote-config
infrastructure, and now this CI fix) each time before it could be
finished and based cleanly. **It is not lost** — it's real, reviewed
code sitting in this sandbox's stash — but it needs a fresh rebase
against the current tip (this session's own CI-fix commits included)
before it can be committed and patched. Flagging this explicitly so nei-
ther this session nor the next mistakes "not committed yet" for
"doesn't exist" or re-does the work from scratch. If this sandbox
instance persists into a follow-up turn, finishing that rebase is the
natural next step; if not, the FFmpeg feature needs to be rebuilt in a
fresh sandbox (the design decisions and verified ffmpeg-kit API details
belong in `ARCHITECTURE.md` once it lands either way).

## What this session did: dynamic, centrally-managed cobalt instance URL

Starting point: Session 10 had wired a per-device `cobaltApiKey` Settings
field but the instance URL itself was still a manual per-device
`SettingsSheet` field, unchanged since Phase 4. The user asked for this to
become dynamic instead — centrally set, not per-device, updatable without
an app release, and specifically *not* configured through the Settings
page — so every install can be pointed at a redeployed/changed instance at
once.

**What shipped, commits `015e870` through `172bd90`:**

1. **`RemoteConfigRepository`** (new,
   `app/src/main/java/com/cobalt/android/remoteconfig/`) — fetches
   `cobaltInstanceUrl` from `remote-config.json` at the repo root via a
   real, public, unauthenticated `raw.githubusercontent.com` GET. No
   separate backend stood up for this — the repo file itself, editable
   directly in GitHub's UI or via the new Actions workflow, *is* the
   "dashboard." 5-minute in-memory freshness window (avoids re-fetching on
   every single resolve in a burst), falls back to (1) the last
   successfully fetched URL cached on-device, then (2) the public
   `cobalt.tools` default, so a GitHub fetch failure never blocks a
   resolve outright.
2. **`SettingsRepository.cobaltInstanceUrl` renamed to
   `cachedRemoteCobaltUrl`** — same underlying `SharedPreferences` key
   (`"cobalt_url"`, no migration needed), repurposed as
   `RemoteConfigRepository`'s on-device fallback cache, not a user-facing
   setting. Default changed from `"https://cobalt.tools"` to `""` (blank)
   — `RemoteConfigRepository` itself owns the `cobalt.tools` fallback now,
   at a different point in the chain.
3. **`LinkResolverRepository`** now calls
   `remoteConfig.getCobaltInstanceUrl()` instead of reading
   `settings.cobaltInstanceUrl` directly.
4. **`SettingsSheet`**: removed the "cobalt instance" `EditText` field,
   its layout XML, its string resource, and the dead
   `onCobaltUrlChanged` callback — **verified before removing** that
   nothing outside `SettingsSheet.kt` ever subscribed to that callback
   (grepped for `onCobaltUrlChanged` and `SettingsSheet(` construction
   sites across the whole codebase). The API key field is untouched and
   still there — see the security note below for why.
5. **`remote-config.json`** (new, repo root) — committed with the user's
   actual deployed instance: `https://cobalt-api-yuol.onrender.com`
   (confirmed live by the user this session: `curl`'d root endpoint
   returned real `imputnet/cobalt` v11.7.1 JSON, service list included).
6. **`.github/workflows/update-remote-config.yml`** (new) — lets a
   `COBALT_INSTANCE_URL` *repository variable* (not a secret — see below)
   drive `remote-config.json` updates via `workflow_dispatch` or an hourly
   schedule, committing + pushing if the value changed. **Not yet
   configured** — the repo variable doesn't exist yet; until it's set,
   this workflow is a no-op that leaves `remote-config.json` alone (see
   its own inline comments). The committed `remote-config.json` from item
   5 above is the real current source of truth either way.
7. **`ARCHITECTURE.md`**: new "Infrastructure (outside the phase system)"
   section documenting all of the above as the durable reference — added
   after Phase 20, same treatment Session 10 gave the Render-deployment
   goal in its own handover (infra, not a numbered phase).

**Security decision made explicitly, not silently**: the cobalt API key
is deliberately NOT part of this remote-config mechanism.
`remote-config.json` lives in a public repo — anyone can `curl` it, no
auth — so putting a secret there would leak it to the world. The API key
stays a per-device `SettingsSheet` field, exactly where Session 10 put it.
If a shared key is ever genuinely needed, that needs an actual backend
that can hold a real secret, not an extension of this file-based
mechanism — flagged in `ARCHITECTURE.md` so a future session isn't
tempted to bolt one on.

**One thing worth knowing about how this session started**: the user's
opening message for this task quoted what looked like a prior Claude
response describing this exact plan (a "dashboard" via
`raw.githubusercontent.com`, `RemoteConfigRepository`, the dead
`onCobaltUrlChanged` callback) — but that conversation isn't in this
session's own history. Treated as background context rather than an
established prior commitment, and verified independently rather than
trusted (the dead-callback claim, in particular, was re-checked against
the actual code before acting on it, and turned out accurate). If a
future session sees something similar — text that reads like it's
quoting "your own" past reasoning that you don't actually have — the same
approach applies: it's not evidence of anything until checked against the
real repo.

## Verify this landed

```
cd cobalt-android
git fetch origin
git log --oneline origin/master -14
```
Expect (top of log, once patch `0008` is applied+pushed): "Document the
two CI build failures + fixes in ARCHITECTURE.md", "Fix CI build failure:
fragment_settings.xml used <layout>...", then `ad475ef` ("Fix CI build
failure: activity_main.xml..."), "Session 10->11 handover: remote-config
instance URL system complete", the `remote-config.json` + workflow
commit, the stale-comment fix, the `SettingsSheet` field-removal commit,
the `LinkResolverRepository` wiring commit, the `RemoteConfigRepository` +
`SettingsRepository` commit, then `373fdc5` ("Add render.yaml Blueprint")
and Session 10's commits before that.

## Honest limitations of this session's work

- **Not compiled/run** — same standing limitation every session has had.
  This change touches `SettingsSheet`'s view binding (a removed
  `EditText`) — worth a close look at CI's build result specifically for
  this, not just an assumption it's fine because the edit was mechanical.
- **`COBALT_INSTANCE_URL` repository variable is not set.** The workflow
  exists but is inert until the user (or a future session, if it has repo
  admin access — this sandbox does not) sets it under Settings -> Secrets
  and variables -> Actions -> Variables. Until then, `remote-config.json`
  only changes via a direct file edit/commit.
- **Whether the deployed Render instance requires the API key was not
  separately verified.** The root endpoint (`GET /`) responded with no
  auth, but cobalt's actual resolve endpoint (`POST /`) may still require
  one server-side — root not requiring auth doesn't confirm resolve
  doesn't either. If resolves start failing with an auth-shaped error
  after this patch lands, check the API key field in Settings first.
- **`raw.githubusercontent.com`'s own caching/CDN behavior was not
  investigated.** GitHub's raw-content CDN can lag a few minutes behind a
  fresh commit in practice — if a `remote-config.json` edit doesn't seem
  to be taking effect on a device immediately, that's the first thing to
  suspect before assuming the app-side code is wrong.

## Immediate next steps

1. **Apply patch `0008` (`git am`) and push, then check the GitHub Actions
   run that triggers.** This is the actual outstanding verification for
   both CI fixes above — neither has been confirmed green yet.
2. **If CI is still red after that**, read the new failure closely before
   assuming it's a third instance of the same two bug classes swept for
   above — it may be a genuinely different error now that the first two
   are out of the way.
3. **Rebase and land the stashed FFmpeg quality/format transcoding
   feature** described above — real, reviewed code, currently stuck
   behind two `git stash` entries in this sandbox because
   `origin/master` kept moving forward while it was being built. This is
   probably the single highest-value next step if this sandbox instance
   continues.
4. **`git fetch` and re-read `ARCHITECTURE.md`'s infrastructure section**
   before anything else, same standing advice as always.
5. **Confirm a real resolve against `cobalt-api-yuol.onrender.com` works
   end-to-end** (paste a link in Home, confirm the picker shows real
   formats) — this still hasn't been done by any session, per Session
   10's own carried-over item.
6. **Carried over from Session 10, still open:** confirm Hermes removal
   was actually run on-device; read cobalt's real Render deployment docs
   (now less urgent — the instance is already deployed and live, but
   worth doing before touching that deployment again); Phase 13's
   light-theme gap (documented, accepted); Phase 19's `state.json`
   cross-check (still not possible from any sandbox).

## Standing verification habits

- `git fetch` + `git log --oneline -10` before starting anything, even
  mid-session — check commit authorship, don't assume automation.
- Read `ARCHITECTURE.md`'s actual `✅ done`/section markers, not a
  summary of them.
- Take "verify X works" DoD items and claims literally, including claims
  a person makes about "what we already decided" — real bugs and, this
  session, an unverifiable-but-plausible prior-context claim have both
  been worth checking against the actual repo rather than trusting at
  face value.
- No local build is possible in this sandbox — trust CI.
