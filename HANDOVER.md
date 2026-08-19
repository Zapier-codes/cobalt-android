# Cobalt-Android — Handover (Session 13, this session)

**Start by cloning the repo fresh: `git clone https://github.com/Zapier-codes/cobalt-android.git`**
Do not trust any file content quoted in this document as current —
clone, then read the real files. `git fetch` + `git log --oneline -10`
before starting anything, even mid-session — parallel manual sessions
happen often on this repo (see Actors section).

## Continuation (part 3, same session): the real ffmpeg-kit fix + a CI
## workflow bug that was making runs look inconsistent

The user pasted a live CI failure log (run "Update handover with the real
ffmpeg-kit fix and Session 13's full history" — ironic, since that commit's
own claimed fix hadn't actually been verified against a real build yet):
`Could not find io.github.jamaismagic.ffmpeg:ffmpeg-kit-lts-full-gpl-16kb:6.1.7`.

**Root cause, confirmed not guessed:** the artifact/name pinned in
`app/build.gradle.kts` was always correct — `ffmpeg-kit-lts-full-gpl-16kb`
genuinely exists as a published Maven Central artifact (fetched the real
mvnrepository.com page directly, not a search snippet, and confirmed it in
a full 10-artifact group listing). The version was wrong: `6.1.7` had been
inferred from a *sibling* artifact in the same group
(`ffmpeg-kit-lts-16kb`, which genuinely is at `6.1.7`) rather than read off
`ffmpeg-kit-lts-full-gpl-16kb`'s own page — different artifacts in the same
Maven group are not guaranteed to share a version, and here they didn't.
Fetched that artifact's own page directly: it has exactly **one** published
version, **`6.1.4`** (Feb 27, 2026) — now pinned.

**Before committing to this fix, seriously considered swapping to a
different package entirely** (`com.moizhassan.ffmpeg:ffmpeg-kit-16kb`,
confirmed live on Maven Central, found during the same research pass) but
ruled it out for a real, checked reason, not a hunch: its license metadata
is LGPL-only, meaning no GPL codecs (x264/x265/etc). Read
`FfmpegTranscoder.buildArgs()` directly and confirmed it genuinely emits
`-c:v libx264` for `TranscodeProfile.Video` entries with
`videoCodec == "libx264"` — an LGPL-only package would silently fail every
MP4 tier in `TranscodeProfile.ALL_VIDEO` at runtime ("Unknown encoder
'libx264'"), not at compile time, so this would NOT have shown up as a CI
failure — it would have shipped broken and only surfaced when a real user
tried an MP4 download. Checked `minSdk` (26) too, confirming `main` vs
`lts` doesn't matter for this project (both exceed 26) in case a future
session considers that swap for other reasons.

**Second, separate fix this same pass**: the user asked why CI seemed to
"succeed right after failing, almost as if something else is building a
stale or separate version." Read `.github/workflows/build.yml` directly —
found `on: push` AND `on: pull_request` both configured for
`branches: ["**"]`. This project's real workflow is direct commits (patch
+ `git am` + push from a device), not PR-gated merges, so a commit that's
also part of an open PR (the log the user pasted referenced "PR #44")
triggers **two separate builds**: one for the exact pushed commit, one for
a GitHub-synthesized PR merge commit. These are genuinely different trees
and can diverge in Gradle cache warm-state, producing different pass/fail
results for what looks like "the same" commit. **Removed the
`pull_request` trigger** — one trigger, one build, one unambiguous result
per commit. (The `actions/cache` `restore-keys` fallback in this same file
was also considered as a contributor — a primary-key cache miss falls back
to the most recent same-OS cache regardless of which Gradle files produced
it — but this doesn't fully explain a *nonexistent* artifact resolving,
since Gradle always re-checks declared repositories for anything not
already present at the exact requested coordinate+version; flagged as a
secondary, unconfirmed possibility, not fixed, since removing the
dual-trigger bug alone should already resolve the confusing behavior
described.)

**Not yet done:** an actual green Actions run confirming this — same
standing limitation as always, this sandbox can't watch or trigger CI.
This is genuinely the fourth ffmpeg-kit dependency attempt; if a
`Could not find` error appears again on this exact line, do not guess a
fifth fork — fetch `ffmpeg-kit-lts-full-gpl-16kb`'s own mvnrepository.com
or central.sonatype.com page directly first, the same way this fix did.

---

## Workflow (unchanged since Session 9)

- **No local build possible in any sandbox** — no route to
  `services.gradle.org`, no Android SDK. GitHub Actions CI (`build.yml`)
  is the only real build authority. Every fix in this repo's history is
  reasoned from source + external verification (Maven Central, official
  docs), never from an actual compile — say so plainly when reporting
  a fix, and don't assume a prior fix claim is correct just because it
  says so in a commit message. **This session found a third example of
  this** (see below) — a fix that looked verified (confirmed the
  dependency existed, confirmed its docs mentioned matching class names)
  but had verified the wrong thing.
- **Only `git add` + `git commit`. Never `git push`.** Generate patches
  (`git format-patch <base>..HEAD -o /mnt/user-data/outputs`) and
  present them — the user applies via `git am` + `git push` on-device.
- When a CI log is pasted, **verify every claimed fix against the
  actual current file content and, for dependency issues, what the
  package is actually built for** — not just whether it resolves.
  `73c7514`/`decefa3` both claimed the same fix that a later CI log
  showed still failing. This session's `b74b1c1` is a second instance:
  the *previous* "fix" (`c055efb`, "verified against sources not
  assumed") really did confirm the antonkarpenko coordinate resolves and
  contains GPL codecs — it just never checked whether that artifact was
  built to be imported directly by a plain Android app at all, and it
  isn't (see below).

## Actors on this repo (unchanged from Session 10/12, still true)

Two kinds of actor, no others:
1. **Manual chat sessions** (this one, "Session N") — a human opens a
   chat, works one step, hands off a patch. This is the only thing
   moving the project forward.
2. **The autonomous Hermes watch-loop** — confirmed fully deleted from
   the user's device as of Session 10. Not verified from any sandbox —
   take on the user's word, but the architecture conclusion (only manual
   sessions now) holds.

## What Session 13 did, most recent work first

### Root-caused and fixed the ffmpeg-kit dependency for real (commit
### `b74b1c1`) — the earlier fix in this same session was incomplete

A pasted live CI log for commit `1807e61` (this session's own prior
handover commit — i.e. the CI run *for* the "three CI fixes root-caused
and landed" commit) showed the build still failing, but on a
**different** error than any of the three this session had already
fixed: `Unresolved reference: arthenica` on every `FFmpegKit`/
`FFmpegKitConfig`/`FFmpegSession`/`ReturnCode`/`Statistics` import in
`FfmpegTranscoder.kt`. The dependency itself resolved without error —
CI's log shows the native `.so` files (`libavcodec.so`, `libffmpegkit.so`,
etc.) being packaged successfully — so this wasn't a "could not find the
coordinate" problem like the earlier `2.2.1`-vs-`2.1.0` version-pin bug.

Root cause, confirmed by fetching the actual artifact's own listing
(not re-reasoning from the same GitHub issue the previous fix leaned
on): `com.antonkarpenko:ffmpeg-kit-full-gpl` (pub.dev:
`ffmpeg_kit_flutter_new_full`, repo `sk3llo/ffmpeg-kit-flutter`) is a
**Flutter plugin build** — meant to be consumed through Flutter's Dart
bridge, not imported directly as a plain Android Kotlin/Java library.
The earlier fix's verification (a crash log from that fork's issue
tracker mentioning `com.arthenica.ffmpegkit.NativeLoader`/
`FFmpegKitConfig` in a stack trace) was real, but didn't actually prove
what it was used to justify — a class name appearing in someone else's
crash trace isn't the same claim as "this AAR exports that class for
direct import," and it doesn't.

**Fixed**: swapped to
`io.github.jamaismagic.ffmpeg:ffmpeg-kit-lts-full-gpl-16kb:6.1.7`
(`JamaisMagic/ffmpeg-kit-16KB`) — verified this one differently and more
directly: fetched its `android/README.md` and confirmed it's the
**unmodified original** arthenica Android documentation (same `import
com.arthenica.ffmpegkit.FFmpegKit;` line, same full API), and confirmed
via Maven Central's group listing that `ffmpeg-kit-lts-full-gpl-16kb` is
a real, separately-published artifact under that group (distinct from
the plain `ffmpeg-kit-lts-16kb`, which is LGPL-only and does NOT include
x264/x265 — picking that one instead would have repeated the exact
"silently drop the GPL codecs" mistake `ARCHITECTURE.md` already warns
about for a different reason). Full detail, including the honestly-
flagged uncertainty around the exact `6.1.7` version number (a best-
match against sibling artifacts, not read directly off this specific
artifact's own Maven page — that page didn't render its version table
in this sandbox's fetch tooling), is in `FfmpegTranscoder.kt`'s
`DEPENDENCY_NOTE` KDoc and `app/build.gradle.kts`'s inline comments —
read those before touching this dependency again.

**Not yet confirmed by an actual CI run** — same standing caveat as
every fix in this file's history. If CI fails again on this exact line
with a "could not find" error (not an unresolved-reference compile
error), that means the `6.1.7` version guess was wrong, not that the
whole approach was — see the DEPENDENCY_NOTE for what to check.

### Earlier this session: fixed three real CI compile errors (commit
### `c055efb`), root-caused independently rather than trusting two prior
### "fixed" claims

A pasted CI log (from PR/commit `decefa3`) showed the build still
failing on three errors `73c7514` had already claimed to fix.
Investigated each from scratch and fixed:
1. `app/build.gradle.kts`: `com.antonkarpenko:ffmpeg-kit-full-gpl` was
   pinned to a version (`2.2.1`) that only exists for the plain non-GPL
   `ffmpeg-kit-full` artifact, not the `-gpl` one — confirmed against
   Maven Central's own repository index. Fixed to `2.1.0` (a real
   version for the `-gpl` artifact) — **this fix was necessary but not
   sufficient**; see above, the coordinate itself turned out to be wrong
   for a different reason discovered later this same session.
2. `MainActivity.kt`: wrong `findNavController` import (Fragment-
   receiver extension imported but called from an Activity). Fixed to
   the Activity-receiver overload. **Confirmed fixed** — this error does
   not appear in the later CI log.
3. `QualityOptionAdapter.kt`: invalid Kotlin "definitely non-null type"
   syntax (`TranscodeProfile & Any`) used against a concrete class, not
   a type parameter. Fixed with a plain non-null override. **Confirmed
   fixed** — this error does not appear in the later CI log either.

## Found and reported, not yet acted on (carried over, unchanged)

- **`README.md` describes a completely different app** than what's
  actually built — a WebView wrapper around cobalt.tools, not the
  native app 20+ phases have actually built. Also still points `git
  clone` at the upstream fork parent, not this repo. Flagged to the
  user, no response yet on priority.
- **`cycle-prompt.txt` and `watch-loop.md`** at repo root are leftover
  Hermes-automation infrastructure files, harmless but arguably
  shouldn't still be there. Low priority.

## Standing open items (carried forward)

1. **Confirm the CI run after `b74b1c1` is actually green.** This
   sandbox cannot watch `gh run list` — ask the user directly or wait
   for a pasted log, and actually verify against it rather than assume.
2. **A real end-to-end resolve against the deployed Render instance
   (`cobalt-api-yuol.onrender.com`) has still never been confirmed by
   any session.** Carried over since Session 10.
3. **Phase 20 (FFmpeg transcoding) follow-ups**: Settings UI for the two
   default-quality preference keys, a cancel button for an in-flight
   `CONVERTING` row, wiring the quality sheet into Shorts' save action.
4. **`state.json`'s `architecture_complete` flag** — likely moot now
   that the Hermes loop is gone, never explicitly confirmed moot.
5. Phase 13's light-theme-looks-identical-to-dark gap — documented,
   accepted, not a blocker.
6. README.md rewrite and Hermes-file cleanup, above.

## Verify this session's work landed

```
cd cobalt-android
git fetch origin
git log --oneline origin/master -8
```
Expect `b74b1c1` ("Fix CI build failure: swap ffmpeg-kit fork again...")
at the top once this session's patch is applied and pushed, with
`c055efb` ("Fix all three real CI compile errors...") beneath it.
