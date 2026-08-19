# Cobalt-Android — Handover (Session 10 → next)

**Start by cloning the repo fresh: `git clone https://github.com/Zapier-codes/cobalt-android.git`**
Do not trust any file content quoted in this document or prior handovers as
current — clone, then read the real files.

**This file itself should now be committed at the repo root as
`HANDOVER.md`.** If you don't find it there, this session's work hasn't
landed yet.

## Workflow, unchanged from Session 9: trust the predecessor's handover,
## don't re-verify it, don't build locally, commit-only + patch handoff

- **Do not attempt a local build.** Confirmed impossible in this sandbox
  (no route to `services.gradle.org`, no Android SDK). GitHub Actions CI is
  the real build/verification authority — check `gh run list` or the
  Actions tab, not a local build.
- **Only `git add` + `git commit`. Do not `git push`.** Generate patch
  files (`git format-patch <base>..HEAD -o /mnt/user-data/outputs
  --start-number=<N>`) and present them — the user applies via `git am` on
  their own device and pushes themselves. Check `ARCHITECTURE.md`/prior
  handovers for the last patch number used and continue from there.
- **Trust the previous session's `ARCHITECTURE.md` ✅ markers and this
  file at face value** — don't redo a full from-scratch verification pass.
  A `git fetch` + `git log -10` + skim of `ARCHITECTURE.md`'s done-markers
  is still worth the ~30 seconds; a full re-derivation is not.

## Important correction from this session: "Hermes" is gone. It was two
## separate things, and the automated one has now been fully removed.

Earlier handovers conflated two systems under "Hermes":

1. **An actually-autonomous background loop** (`hermes-pipeline-launch.sh`,
   a `while true` watch-loop on the user's device, launched via
   Termux:Boot + a `.bashrc` resume trigger, calling Hermes Agent against a
   local model-provider router on port 8787). This is the thing that once
   carried the project from Phase 5 to Phase 14 unattended. **The user has
   now fully deleted this** — Hermes Agent itself, the launcher script, the
   boot trigger, the `.bashrc` resume trigger, and the fallback router are
   all removed from the device as of this session. It cannot restart
   itself; there is no lockfile/checkpoint to resume from anymore.
2. **Separate manual Claude chat sessions** ("Session 6", "Session 7",
   "Session 8", "Session 9" in prior handovers, this one is "Session 10")
   — a human explicitly opening a chat, asking Claude to check
   `ARCHITECTURE.md` and build the next phase, and manually applying the
   resulting patch via `git am`. **This is still how the project moves
   forward**, and is exactly what's happening in this session. If you are
   reading this file as part of one of these sessions: you are actor #2,
   not #1. Don't assume anything is running unattended anymore — nothing
   is. A future session's own git-authored commits (this sandbox uses
   `Claude <claude@anthropic.local>`) are not evidence of an automated
   pipeline; they're evidence of a manual session like this one.

**Going forward, `git log` showing commits appear "on their own" between
sessions still means what earlier handovers warned about — a different
manual chat session did work in parallel, not that automation restarted.**
Always `git fetch` before starting, same as before.

## What Session 10 did

### Full "no stubs / no placeholders" cross-check (ARCHITECTURE.md's own
### standing rule) — one real gap found and fixed

Grepped every `.kt`/`.xml` file for TODO/FIXME/stub/placeholder/hardcoded/
not-implemented markers. Everything that matched was either historical
KDoc referencing a *past* phase's now-resolved gap, or the Phase 13
LIGHT-theme-looks-identical-to-DARK limitation already documented and
accepted in `ARCHITECTURE.md` — nothing new.

**One real, previously-undocumented gap:** the app could never send an
API key to a cobalt instance. Checked cobalt's actual API contract
(`github.com/imputnet/cobalt`, `docs/api.md`) — real auth scheme is
`Authorization: Api-Key <value>`, and nothing in `SettingsRepository`,
`SettingsSheet`, or `LinkResolverRepository` had any concept of it at
all. This matters immediately: the user's next step (below) is pointing
the app at a self-hosted Render instance, which — if configured with
API-key auth, which most private/self-hosted instances are, precisely to
avoid being an open proxy — would have silently 401'd with no way to
configure around it from the app.

**Fixed, commit `1adc548`:**
- `SettingsRepository.cobaltApiKey`: new persisted string. Blank (the
  default) = send no `Authorization` header at all, matching the public
  `cobalt.tools` no-auth-required default.
- `SettingsSheet`: new `etCobaltApiKey` field directly under the existing
  instance-URL field, same layout/styling, same persist-on-`onStop()`
  pattern as the URL field — except blank is a *meaningful* value here
  ("stop sending a key"), so unlike the URL field it always writes, never
  skips on blank.
- `LinkResolverRepository.resolveFromNetwork()`: sets
  `Authorization: Api-Key <key>` on the resolve request, only when a key
  is actually configured.

Not yet generated as a patch file as of this handover being written —
**do this first if picking up mid-session** (see Immediate next steps).

### Hermes removal

See the correction section above — this session gave the user the exact
on-device removal commands (Termux `.bashrc`/boot script, Ubuntu
container's Hermes Agent install, launcher script, lockfile dir, fallback
router). Not verified from this sandbox (can't reach the user's device) —
**confirm with the user it was actually run before assuming Hermes is
gone**, though the architectural conclusion (manual sessions are the only
actor now) holds regardless once they do.

## The user's actual next goal: deploy a cobalt instance to Render, wire
## its URL + API key into the app, make it "fully active"

This is **not an `ARCHITECTURE.md` phase** — all 20 phases are the
Android app itself; deploying the backend cobalt instance is
infrastructure the app has always assumed exists (the app talks to
*some* cobalt instance URL, configurable in Settings, defaulting to the
public `cobalt.tools`). Nothing in the Kotlin/Gradle side needs a new
phase number for this — Phase 20's "final gate" language is about the
Android codebase being stub-free, which the audit above confirms it is
(modulo the `state.json` note from Session 9, still applicable, still not
something this sandbox can touch).

**Not yet started as of this handover.** Concretely still needed:
1. Deploy `imputnet/cobalt`'s API service to Render (Docker-based, per
   cobalt's own deployment docs — not yet read/verified by this session,
   do that first, don't assume the exact Render steps).
2. Decide whether to turn on API-key auth on that instance (cobalt
   supports running with no auth, API-key auth, or JWT/Turnstile auth —
   the app as of this session's commit only supports the no-auth or
   API-key cases, not JWT/Turnstile session flow).
3. Enter the deployed instance's URL + (if used) API key into the app's
   Settings sheet (`SettingsSheet`'s two fields, both now wired) — this
   is a manual on-device step for the user, not something to build.
4. Confirm a real resolve against the new instance actually works
   end-to-end (paste a link in Home, confirm the picker shows real
   formats) — genuinely verify, don't just confirm the request compiles.

## Immediate next steps

1. **Generate and present the patch for commit `1adc548`** (API key
   wiring) if this session is being continued — check what patch numbers
   prior sessions used (search `ARCHITECTURE.md`/old handovers, or ask)
   and continue the sequence.
2. **Confirm with the user whether Hermes removal was actually run.**
3. **Read cobalt's actual Render deployment docs** before advising on
   step 1 of the deployment goal above — this session did not do that
   yet, only confirmed the *auth* contract via `docs/api.md`.
4. **Carried over, still not done** (unverified — check before assuming):
   `AGENTS.md` truncation warning, `.env`'s deprecated `TERMINAL_CWD`
   (both now likely moot if the router was deleted per Hermes removal —
   confirm), leftover `[debug]` logs in `/root/fallback-router/server.js`
   (moot if deleted), Phase 13's light-theme gap (documented, accepted,
   not a blocker), Phase 19's `state.json` cross-check (still not
   possible from any sandbox).

## Standing verification habits (unchanged)

- `git fetch` + `git log --oneline -10` before starting anything, even
  mid-session — check commit authorship, don't assume automation.
- Read `ARCHITECTURE.md`'s actual `✅ done` markers, not a summary of them.
- Take "verify X works" DoD items literally — real bugs have been found
  this way multiple times across sessions 6 and 9.
- No local build is possible in this sandbox — trust CI.
