# Cobalt-Android — Handover (Session 9 → 10)

**Start by cloning the repo fresh: `git clone https://github.com/Zapier-codes/cobalt-android.git`**
Do not trust any file content quoted in this document or prior handovers as
current — clone, then read the real files.

**This file itself should now be committed at the repo root as
`HANDOVER.md`.** If you don't find it there, this session's work hasn't
landed yet.

## Correction: "Hermes Session N" commit authors are separate Claude chat
## sessions, not an autonomous pipeline

Earlier phrasing in this file's history (and in at least one session's own
notes) described this as a self-driving "Hermes pipeline" running
unattended. **That's not accurate — the user runs multiple separate Claude
chat sessions against this same repo in parallel** (this session included),
each committing as `Hermes Session N <hermes-sessionN@cobalt-android.local>`.
There's no autonomous loop deciding what to build next on its own; it's
independent chat sessions, sometimes overlapping in time, working from the
same `ARCHITECTURE.md`. The practical implications this creates are still
real and still apply — see "Important: this repo is worked by multiple
sessions in parallel" below — only the mental model of *why* was wrong.

## Workflow rule (still applies, set two sessions ago): commit-only, patch
## handoff, no local build

- **Do not attempt a local build** (`./gradlew ...` or otherwise). Confirmed
  impossible in this sandbox as of Session 7 (no route to
  `services.gradle.org`, no Android SDK) and unchanged since. GitHub
  Actions CI is the real build/verification authority — `cycle-prompt.txt`'s
  MAINTAIN MODE already checks it via `gh run list`, once Phase 20 turns
  that mode on (see "Immediate next steps" below — it hasn't yet).
- **Only `git add` + `git commit`. Do not `git push`.** This sandbox can't
  reach the user's device. The user applies commits via `git am` on their
  own machine and pushes themselves.
- **A session isn't finished until patch files are generated and
  presented** via `present_files` — an unpushed local commit with no
  patch file handed over is a dead end for the user. At the end of a
  session:
  1. `git fetch origin` then find the base commit — the last one already on
     `origin/master` before this session's own commits.
  2. `git format-patch <base>..HEAD -o /mnt/user-data/outputs
     --start-number=<N>`, continuing whatever numbering is already in use.
     **This session could not determine Session 8's actual patch numbers
     with confidence** (see "Note on patch numbering" below) — check
     `/mnt/user-data/outputs` and ask the user which number their Downloads
     folder is actually up to if there's any doubt, rather than guessing
     and risking a collision.
  3. `present_files` the result(s) — don't just report a path in prose.
  4. Give the user the exact push sequence:
     ```
     cd /root/projects/vid/cobalt-android
     git fetch origin
     git reset --hard origin/master
     cp /mnt/sdcard/Download/000N-....patch .
     git am 000N-....patch
     [repeat cp/git am per patch, in order]
     git push
     ```

### Note on patch numbering (read before generating this session's patches)

Session 7's handover said Session 8 should start at `0007`. Session 8's
`HANDOVER.md` update (`85644bc`) documented the *process* for numbering
but — per the gap noted just below — **never actually recorded what
numbers it used**, and Session 8's Phase 18 work reached `origin/master`
(`9ba78db`/`90b365c`) without this file being updated to say so. This
session generated its own Phase 19 patch as `0009` (one commit, on top of
`90b365c`) as a reasonable continuation, but could not verify against
Session 8's real numbers that this doesn't collide with something already
sitting in the user's Downloads folder. **Whoever picks this up next:
confirm with the user what their Downloads folder is actually up to
before trusting `0009` as correct, and please actually record your own
patch numbers in this file when you hand off — don't repeat this gap.**

## Real gap found and fixed this session: Session 8 never wrote a
## Session 8 → 9 handover

This session (`git fetch`+`git log` on arrival) found `origin/master` had
already advanced past Session 7's last-known point — Phase 18
(`9ba78db`/`90b365c`, authored `Hermes Session 8`) was on `origin/master`,
but `HANDOVER.md` still read "Session 7 → 8" with no record of Session 8's
work, its reasoning, or its known limitations anywhere in this file. This
session's local unpushed Phase 18 work (built before discovering Session
8's had already landed) was discarded via `git reset --hard origin/master`
rather than merged, to avoid duplicating/conflicting with the real,
already-pushed implementation — same handling precedent as the earlier
Phase 8-17 collision described further down this file.

**What Session 8 actually did, reconstructed from its real commits and
diffs** (not just trusted from a summary, since none existed — this was
verified against the actual `9ba78db` diff):

### Phase 18 — Performance: player lifecycle discipline done

Two DoD items, both done — commit `9ba78db` (content), `90b365c`
(ARCHITECTURE.md mark-done):

1. **Verified, not assumed**, `ShortsFragment`'s and
   `VideoPlayerDialogFragment`'s (Phase 8) pause/release discipline — both
   already correct; `VideoPlayerDialogFragment` needed no code change,
   only the read-through confirmation.
2. **Next-item preloading**: a second, silent `ExoPlayer`
   (`ShortsFragment.preloadPlayer`, `playWhenReady = false`, never attached
   to a `PlayerView`) buffers the next Shorts item ahead of a swipe.
   `playAt()` swaps it in as the main player when the target position
   matches what was preloaded, instead of cold-starting
   `setMediaItem()`/`prepare()` every time — the swap is the actual point
   of preloading. `buildMediaItem()` was extracted so the main and preload
   paths build `MediaItem`s identically.

Full detail, including all three honestly-stated known limitations (no
on-device leak verification possible from this sandbox, preload is exactly
one item ahead not deeper, no metered-connection awareness), is in
`ARCHITECTURE.md`'s Phase 18 write-up — read that, not this summary.

## What this session (9) did

### Phase 19 — Full "no stubs" audit done

One commit, `b748599`. Full detail — including the honest scope statement
of exactly which files got a full re-read vs. which were covered only by
the file-existence sweep and the stub-pattern grep — is in
`ARCHITECTURE.md`'s Phase 19 write-up; **read that in full before assuming
this phase means "everything was individually re-read."** It doesn't — six
specific high-risk files (money-path logic, plus `ShortsViewModel.kt`
specifically since that's the exact file class that hid fake data behind
clean prose once before) got a genuine full-file read against their DoD;
everything else got existence-checked and grep-swept. Stated that way on
purpose rather than overclaiming.

**Headline findings:**
- Zero TODOs, zero stubs, zero fake/mocked data found anywhere touched.
- `ShortsViewModel.kt` (the file Session 4 originally found fully
  hardcoded) is confirmed real today — every method traced to a real
  repository call.
- Two trivial, non-blocking nits (a doc-comment typo, a harmless redundant
  default-value write in `SettingsFragment.onPause()`) — noted in
  `ARCHITECTURE.md`, not fixed, not worth their own cycle.
- `git log` backs every phase 1-18 with real commits, including visible
  evidence of the collisions this file already documents (Phase 5's
  three-commit merge, Phase 14's revert+reapply).
- `state.json` is **not part of this repo** — it's the on-device
  session-runner's own local state, never committed. DoD-3's "cross-check
  against `state.json`'s `last_commit_sha` history if available" isn't
  something this sandbox (or `git log` alone) can do. Stated as such
  rather than silently skipped or assumed passing.

## Important: this repo is worked by multiple sessions in parallel

Real, still applies (see correction at the top for the accurate mental
model): the user runs separate Claude chat sessions against this repo,
sometimes overlapping in time — one carried the project from Phase 5 to
Phase 14 while another was doing unrelated work in the same window, and
separately two sessions built Phase 5 independently and collided (resolved
via a 3-commit merge). This session (9) itself found and discarded its own
stale local Phase 18 work after discovering Session 8 had already built and
pushed the real thing. **Always `git fetch` and re-read `ARCHITECTURE.md`
immediately before starting anything, even mid-session** — and if your own
local commits turn out to duplicate something already on `origin/master`,
discard yours via `git reset --hard origin/master` rather than trying to
merge/rebase a competing implementation on top.

## Verify this landed

```
cd cobalt-android
git fetch origin
git log --oneline origin/master -6
```
Per the workflow rule, **this session's Phase 19 commit (`b748599`
locally) was deliberately not pushed** — don't expect to see it on
`origin/master` until the user has pushed it themselves via the patch
below. If missing, that doesn't mean it didn't land — check the local
repo's own `git log` (unpushed), or ask the user whether they've pushed
yet. Once pushed, expect (top of log): the Phase 19 commit, then `90b365c`
(Session 8's Phase 18 mark-done) and the rest of the history below it.
Check commit authorship for anything unexpected, same as always.

## Honest limitations, this session

- Same standing issue as every session since Session 7: **no local build
  possible** in this sandbox. Confirming a real `./gradlew assembleDebug`
  (or the GitHub Actions equivalent) is still the single most overdue item
  across this entire project — every phase from 4 onward has been reviewed
  by eye, never compiler-verified. Phase 20 doesn't require a local build
  from this sandbox specifically, but a real build passing somewhere
  (device or CI) before calling this project actually finished would be
  reasonable regardless of what `state.json` says.
- Phase 19's audit depth is honestly scoped, not exhaustive — see above and
  `ARCHITECTURE.md`'s Phase 19 write-up for exactly what was and wasn't
  individually re-read.

## Immediate next steps

1. **Confirm this session's patch (`0009`, or whatever number it actually
   turns out to be — see "Note on patch numbering" above) has been applied
   via `git am` and pushed** before assuming `origin/master` reflects
   Phase 19 and this handover update.
2. **Phase 20 — Final gate — is the only phase left, and it cannot be
   fully closed from this sandbox.** Per its own Definition of Done:
   - DoD-1 (Phases 1-19 all individually done, verified by inspecting the
     actual repo) — **this is now true**, per Phase 19's audit above and
     every phase write-up in `ARCHITECTURE.md` reading done.
   - DoD-2 (set `architecture_complete: true` in `state.json`, the only
     condition under which CI/build-error MAINTAIN MODE begins) —
     **`state.json` lives on the user's device, not in this git repo** (see
     Phase 19's finding above). No chat-sandbox session can read or write
     it. **Whoever has access to the actual device/`state.json`** (the
     user, or a session with local filesystem access to it) needs to do
     this step directly — it isn't a "next phase to build," it's flipping
     one flag in a file this environment has never had access to.
   - **If `state.json` turns out not to actually exist, or the user has
     moved away from that file as the real source of truth** (this
     document itself already says `ARCHITECTURE.md`'s own done markers
     win over `state.json`'s `current_phase` field when they disagree) —
     worth asking the user directly whether Phase 20's `state.json`
     requirement is still meaningful to them, or whether "all 19 phases
     verified done in `ARCHITECTURE.md`" is itself the real finish line
     at this point.
3. **Do not attempt a local build** — still confirmed impossible, still
   not a "try harder" situation, see above.
4. **If the AI-generated-content feature comes up again** (declined in
   Session 6, see the older section of this file's git history / the git
   blame on this paragraph if it's been trimmed): the mitigated version
   (separate labeled section, fiction-only, no blending into the real
   feed) is fine to build; the unlabeled/blended/unattended version is
   not, regardless of how the request is framed.
5. **Carried over from sessions 1-5, still not done** (status unverified
   this session — check before assuming): `AGENTS.md` truncation warning,
   `.env`'s deprecated `TERMINAL_CWD`, leftover `[debug]` logs in
   `/root/fallback-router/server.js`, `Termux:Boot` not firing
   `BOOT_COMPLETED`, `~/router.log` provider-health re-check. None of
   these block Phase 20 — they're separate from `cobalt-android` itself
   (device/pipeline plumbing, not app code) — but they're still open.

## Standing verification habits

- `git fetch` + `git log --oneline -10` on `origin/master` before starting
  anything — check commit authorship, confirm nothing unexpected landed.
- Read `ARCHITECTURE.md`'s actual done markers, not a prior handover's
  phase count or summary.
- Take "verify X works" DoD items literally — this file has three examples
  now of a "confirm it's wired up" step surfacing a real, previously-
  unknown bug (Phase 15's shared-cursor exhaustion, Phase 16's duplicate
  History writes, and this session's re-confirmation that
  `ShortsViewModel.kt` genuinely isn't the hardcoded file it once was)
  rather than being a formality.
- **Write this file before ending your session**, even if nothing else
  feels noteworthy — the gap this session found and fixed (Session 8
  landing real work with zero handover record of it) is exactly the
  failure mode this section exists to prevent, and it still happened once.
- Do not attempt a local build — confirmed impossible in this sandbox.
