# Cobalt-Android — Handover (Session 13, this session)

**Start by cloning the repo fresh: `git clone https://github.com/Zapier-codes/cobalt-android.git`**
Do not trust any file content quoted in this document as current —
clone, then read the real files. `git fetch` + `git log --oneline -10`
before starting anything, even mid-session — parallel manual sessions
happen often on this repo (see Actors section).

## Workflow (unchanged since Session 9)

- **No local build possible in any sandbox** — no route to
  `services.gradle.org`, no Android SDK. GitHub Actions CI (`build.yml`)
  is the only real build authority. Every fix in this repo's history is
  reasoned from source + external verification (Maven Central, official
  docs), never from an actual compile — say so plainly when reporting
  a fix, and don't assume a prior session's "fixed" commit actually was
  just because it says so (Session 13 caught two false-fixed claims
  this session — see below).
- **Only `git add` + `git commit`. Never `git push`.** Generate patches
  (`git format-patch <base>..HEAD -o /mnt/user-data/outputs`) and
  present them — the user applies via `git am` + `git push` on-device.
- When a CI log is pasted, **verify every claimed fix against the
  actual current file content and, for dependency issues, the real
  package registry** — don't re-explain a previous session's reasoning
  as if it's confirmed correct. Two commits in this repo's history
  (`73c7514`, `decefa3`) both *claimed* to fix the same three compile
  errors a pasted CI log later showed still failing identically.

## Actors on this repo (unchanged from Session 10/12, still true)

Two kinds of actor, no others:
1. **Manual chat sessions** (this one, "Session N") — a human opens a
   chat, works one step, hands off a patch. This is the only thing
   moving the project forward.
2. **The autonomous Hermes watch-loop** — confirmed fully deleted from
   the user's device as of Session 10 (Hermes Agent, its launcher
   script, boot trigger, `.bashrc` resume trigger, fallback router all
   removed). Not verified from any sandbox — take on the user's word,
   but the architecture conclusion (only manual sessions now) holds.

Occasional unfamiliar git author strings (`Verify Sandbox
<verify@sandbox.local>`, `Michael Erickson <...>` — the user's own
name, from a direct GitHub UI action) have shown up in history. Neither
turned out to be a new automated actor on inspection — but check, don't
assume, if a new one appears.

## What Session 13 did

### Fixed three real CI compile errors (commit `b98d844`), root-caused
### independently rather than trusting two prior "fixed" claims

A pasted live CI log (from PR/commit `decefa3`, itself just a
comments-only fix attempt) showed the build still failing on the exact
same three errors `73c7514` had already claimed to fix. Investigated
each from scratch:

1. **`app/build.gradle.kts`**: `com.antonkarpenko:ffmpeg-kit-full-gpl`
   was pinned to `2.2.1` — verified against Maven Central's own
   repository index (`repo1.maven.org/maven2/com/antonkarpenko/
   ffmpeg-kit-full-gpl/`) that this version **does not exist** for the
   `-gpl` artifact; real versions are `1.0.0/1.0.1/1.1.0/2.0.0/2.0.1/
   2.1.0`. `2.2.1` *is* real, but only for the plain non-GPL
   `ffmpeg-kit-full` artifact — this fork versions the two independently
   and the GPL variant lags behind. Fixed: pinned to `2.1.0`. Full
   verification trail and a note on not trusting a CI log's apparent
   partial success (native `.so` files appearing to package despite a
   bad pin — almost certainly `build.yml`'s `restore-keys` prefix-
   fallback serving leftovers from a previous run's *different*,
   working dependency) is inline in the commit and the file's own
   comments — read those before touching this dependency again.
2. **`MainActivity.kt`**: imported
   `androidx.navigation.fragment.findNavController` (the no-arg
   Fragment-receiver extension) but calls it as `findNavController(
   R.id.navHost)` from an *Activity*. Fixed: import
   `androidx.navigation.findNavController` (the Activity-receiver
   overload, top-level `androidx.navigation` package). Confirmed both
   call sites and no other `findNavController` usage exists anywhere
   else in the codebase.
3. **`QualityOptionAdapter.kt`**: `DIFF`'s override used
   `TranscodeProfile & Any` (Kotlin's "definitely non-null type"
   syntax) — invalid here because that syntax only applies when the
   left side is an actual generic type *parameter* with a nullable
   bound in scope; `TranscodeProfile` is a concrete class in an
   anonymous `object : DiffUtil.ItemCallback<TranscodeProfile?>()`,
   not a type parameter. Fixed: override with the plain non-null
   `TranscodeProfile` type directly — correct per how Kotlin/Java
   interop actually handles a Java `@NonNull`-annotated generic method
   against a nullable Kotlin type argument. Safety property (never
   invoked with a real null, per `AsyncListDiffer`) unchanged.

**Not yet confirmed by an actual CI run** — that's the immediate next
step, see below.

### Found and reported (not yet acted on): README.md describes a
### completely different app than what's actually built

`README.md` at the repo root (and thus the GitHub-rendered repo
homepage) describes a **WebView wrapper around cobalt.tools** — full-
screen WebView, share-sheet trigger, WASM FFmpeg inside the WebView —
none of which matches the actual native app `ARCHITECTURE.md` specifies
and 20+ phases have built (native Shorts feed, native resolution
picker, native FFmpeg transcoding via `FfmpegTranscoder`, dynamic
remote-config instance URL, etc.). It also still points `git clone` at
the upstream fork parent (`Andro-Meta/cobalt-android`), not this repo.
This is almost certainly a leftover from before the `ARCHITECTURE.md`
rewrite that was never updated. **Not fixed this session** — flagged to
the user, no response yet on priority. Worth a full rewrite matching
the actual native architecture whenever there's room for it.

### Also found: two Hermes-pipeline files still committed at repo root

`cycle-prompt.txt` and `watch-loop.md` are the *automation's own*
prompt/instructions files (per-cycle build-mode prompt and the CI
watch-loop's resume logic) — infrastructure for the now-deleted
autonomous loop, not app source. They're harmless sitting in the repo,
but arguably shouldn't be there long-term. **Not removed this
session** — low priority, hasn't blocked anything, flagging for
whenever it's convenient.

## Standing open items (carried forward, still true as of this session)

1. **Confirm the CI run after `b98d844` is actually green.** This
   sandbox cannot watch `gh run list` — ask the user directly or wait
   for a pasted log.
2. **A real end-to-end resolve against the deployed Render instance
   (`cobalt-api-yuol.onrender.com`) has still never been confirmed by
   any session** — paste a link in Home, confirm the picker shows real
   formats. Carried over since Session 10.
3. **Phase 20 (FFmpeg transcoding) follow-ups**, per its own documented
   scope boundaries: Settings UI for the two default-quality preference
   keys, a cancel button for an in-flight `CONVERTING` row, wiring the
   quality sheet into Shorts' save action.
4. **`state.json`'s `architecture_complete` flag** (the old Phase 20/21
   "final gate") — lives outside the repo, on-device, tied to the now-
   deleted Hermes loop. Likely moot now that the loop is gone, but
   never explicitly confirmed moot.
5. Phase 13's light-theme-looks-identical-to-dark gap — documented,
   accepted, not a blocker.
6. README.md rewrite and Hermes-file cleanup, both from this session,
   above.

## Verify this session's work landed

```
cd cobalt-android
git fetch origin
git log --oneline origin/master -5
```
Expect `b98d844` ("Fix all three real CI compile errors...") at the
top once the patch is applied and pushed.
