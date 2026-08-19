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

## Continuation this session (part 2): FFmpeg quality/format transcoding
## landed as Phase 20 (patches `0011`+)

Confirmed via `git fetch` that the user applied and pushed patches
`0008`–`0010` (the CI fixes below) — `origin/master` had moved from
`ad475ef` to `9937554` with matching content, so those two build fixes
are real and live pending an actual Actions run.

With `origin/master` now at a known, current commit, rebased the FFmpeg
quality/format transcoding feature (previously stuck behind two stash
entries — see the superseded note this replaces) cleanly on top of it:

- `git stash pop` produced exactly two conflicts, both expected and
  mechanical: `strings.xml` (pure addition on both sides, merged by hand)
  and `ARCHITECTURE.md` (the feature's own phase write-up was drafted
  against two now-stale phase numbering schemes in a row — the base kept
  moving before it could land). Reset `ARCHITECTURE.md` to the real
  `origin/master` content and re-inserted the write-up as **Phase 20**
  (before the final gate, which shifted to Phase 21) — the correct slot
  now that Phases 1–19 are confirmed done and 20 was still an open final
  gate, not a numbered build phase, when this was drafted.
- Swept every file the feature touches for stale `Phase 15`/`Phase 18`
  KDoc references left over from the two earlier (wrong) numbering
  attempts and corrected them all to `Phase 20` — `Phase 15` in
  particular now legitimately refers to something else entirely (Shorts
  feed hardening), so those weren't just stale, they were actively
  wrong.
- **Caught a real bug during this same review pass**, the same class
  just fixed twice in `fragment_settings.xml`/`activity_main.xml`:
  `sheet_quality_selection.xml` (new this feature) used `app:` attributes
  (`app:singleSelection`, `app:selectionRequired` on a
  `MaterialButtonToggleGroup`) without declaring `xmlns:app` on its root
  — would have been CI failure #3 in the same family as #1 and #2 above
  had it shipped. Fixed before committing. Also re-ran the repo-wide
  `xmlns:app`/`xmlns:tools` sweep (this time in Python, not the earlier
  shell-loop that turned out fragile with empty `grep -c` output) across
  every staged file, not just the one caught — no other offender.
- Verified no leftover `<<<<<<<`/`=======`/`>>>>>>>` conflict markers
  anywhere in the staged tree before committing (`grep -rn` across every
  staged path), and re-confirmed every staged `.xml` file parses and
  every touched `.kt` file's brace/paren counts balance.

Landed as one commit, `9952aa4`
("Phase 20: FFmpeg-based dynamic quality/format transcoding for
downloads"), full design detail in `ARCHITECTURE.md`'s Phase 20
write-up (video ladder, audio ladder, `FfmpegTranscoder`'s two
previously-caught ffmpeg-kit API bugs, the `parentFragmentManager` fix,
the new `DownloadStatus.CONVERTING` state, etc. — not re-duplicated
here). Both now-superseded stash entries were dropped (`git stash drop`
x2) once their content was confirmed subsumed by this commit.

**Not yet done, same as recorded in `ARCHITECTURE.md`'s Phase 20:** not
built/run against a real toolchain (standing limitation, see workflow
section above); Shorts' save action still bypasses the quality sheet;
no cancel button on an in-flight `CONVERTING` row; no Settings UI for the
two new default-quality preference keys.

## Continuation this session (part 1): CI's first real build run found two
## failures, both now fixed (patches `0008`–`0010`, confirmed applied)

This was a same-session continuation of the remote-config work below —
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
   in `ARCHITECTURE.md`'s "CI build failures" section, read that before
   touching this file again). Diffed with `git diff -b -w` to confirm the
   change is wrapper-only, zero content/behavior change.

Patches `0008`–`0010` covered this; **confirmed applied and pushed** —
`origin/master` had these exact commits (under new hashes from `git am`,
as expected) at the start of this continuation.

**Still not done:** an actual green GitHub Actions run confirming both
fixes together, and now also the Phase 20 commit above — this sandbox
can watch neither `gh run list` nor trigger a re-run. That confirmation
is the first thing to check next session.

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
git log --oneline origin/master -15
```
Expect (top of log, once patch `0011` is applied+pushed): "Phase 20:
FFmpeg-based dynamic quality/format transcoding for downloads", then
"Session 11 continuation: record the CI-fix work..." (`9937554`),
"Document the two CI build failures + fixes in ARCHITECTURE.md", "Fix CI
build failure: fragment_settings.xml used <layout>...", `ad475ef` ("Fix
CI build failure: activity_main.xml..."), "Session 10->11 handover:
remote-config instance URL system complete", and Session 10's commits
below that.

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

1. **Apply patch `0011` (`git am`) and push, then check the GitHub
   Actions run it triggers.** This is the actual outstanding verification
   for the CI fixes AND the new Phase 20 FFmpeg dependency
   (`ffmpeg-kit-full-gpl:6.0-2`) actually resolving from Maven Central —
   none of the three have been confirmed against a real build yet.
2. **If CI is red**, check first whether it's the `ffmpeg-kit-full-gpl`
   dependency failing to resolve (see `FfmpegTranscoder`'s
   `DEPENDENCY_NOTE` KDoc for the fallback fork) before assuming it's a
   new instance of the `xmlns:app`/`xmlns:tools` bug class already swept
   for twice this session.
3. **Phase 20's explicit scope boundaries are real follow-up work, not
   just caveats:** Settings UI for the two new default-quality
   preference keys (follows Phase 13's own pattern exactly); a cancel
   button for an in-flight `CONVERTING` row; wiring the quality sheet
   into Shorts' save action.
4. **`git fetch` and re-read `ARCHITECTURE.md`'s Phase 20 + infrastructure
   sections** before anything else, same standing advice as always.
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
