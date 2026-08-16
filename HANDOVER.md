# Cobalt-Android — Handover (Session 6 → 7)

**Start by cloning the repo fresh: `git clone https://github.com/Zapier-codes/cobalt-android.git`**
Do not trust any file content quoted in this document or prior handovers as
current — clone, then read the real files. This document explains what
changed and why, not what's guaranteed to still be true by the time you
read it.

**This file itself should now be committed at the repo root as
`HANDOVER.md`.** If you don't find it there, this session's work hasn't
landed yet — stop and flag that before assuming any of it is live. See
"Verify this landed" below.

## What Session 6 actually did

This session was scoped to Phase 4 only, per explicit user direction —
Phase 4's work and this handover/architecture update, nothing further:

1. **Confirmed Session 5's work landed** before starting anything new:
   cloned fresh, verified commit `3d0891f` (Phase 3: Home screen shell +
   paste-link UI) was genuinely at HEAD, and read the actual file contents
   of `HomeFragment.kt`/`HomeViewModel.kt`/`fragment_home.xml` rather than
   trusting the commit message alone.
2. **Found that `LinkResolverRepository.kt` already existed** at
   `link/LinkResolverRepository.kt`, added in commit `957411f` — out of
   sequence, before Phase 3 landed, and never wired into `HomeViewModel`.
   It was also low quality: a blocking (non-suspend) `execute()` call using
   the deprecated `HttpUrl.parse` API, against a
   `{instance}/api/resolve?url=` GET contract that doesn't match any real
   cobalt instance's actual API. Rewriting it was treated as in-scope for
   Phase 4 (same precedent as Session 5 fixing pre-existing bugs it found
   while touching a file), not a separate unplanned change.
3. **Rewrote `LinkResolverRepository.kt`** — pushed as commit `0b16dbc` —
   as a `suspend fun resolve(url): ResolveResult` (sealed
   `Success(originalUrl, formats)` / `Error(message)`) run on
   `Dispatchers.IO`, POSTing JSON to `{instance}/` per the real cobalt v7+
   API contract (`docs/api.md` in imputnet/cobalt on GitHub — verified via
   web search, not guessed from training data): `status` one of `error`,
   `rate-limit`, `picker` (multiple formats + optional separate `audio`
   track), or `redirect`/`tunnel`/`stream`/`local-processing` (single
   direct-download URL — different instance versions use different status
   names for the same case, all four handled identically since there's no
   way to know which generation a given `cobaltInstanceUrl` runs ahead of
   the call). Full contract assumptions are documented in the file's top
   doc comment.
4. **Wired `HomeViewModel`/`HomeFragment` to the real repository** — pushed
   as commit `9d00b75`. `HomeViewModel` moved from plain `ViewModel` to
   `AndroidViewModel` (needs `Context` for `SettingsRepository`, same
   reason `ShortsViewModel` already is one). `onSubmit()` now launches
   `repository.resolve(url)` in `viewModelScope`, replacing the old fixed
   "Link resolution isn't implemented yet (Phase 4)." message with a real
   in-flight "Resolving…" state, then either a real success summary (format
   count) or a real, distinct error message per failure mode. Added
   `isResolving`/`resolveResult` `LiveData`. `HomeFragment` now disables
   the submit button/field while `isResolving` is true (prevents
   double-submit against a slow/unreachable instance) but does **not**
   render `resolveResult` yet — that's Phase 6's resolution-picker UI; this
   phase's job was only to guarantee the data is real and present in the
   ViewModel, not to display it.
5. **Marked Phase 4 done in `ARCHITECTURE.md`** — pushed as commit
   `ff16a08` — with the same per-phase write-up convention as Phases 1-3
   (files, Definition of Done checked off, honest known limitations). Also
   documented there: the file lives at `link/LinkResolverRepository.kt`,
   not `repository/LinkResolverRepository.kt` as the original Phase 4 spec
   named it — kept the existing path rather than a same-phase move with no
   functional reason behind it.
6. **Did not touch Phase 5 or anything beyond Phase 4**, per explicit user
   scope for this session — `ResolutionCacheEntity`/`ResolutionCacheDao`
   and the resolution-picker UI are still open, exactly as ARCHITECTURE.md
   already described before this session started.

## Verify this landed

```
cd cobalt-android   # wherever your clone/device path is
git fetch origin
git log --oneline origin/master -6
```
Expect to see (top of log, most recent first): the "Mark Phase 4 done in
ARCHITECTURE.md" commit, then the `HomeViewModel`/`HomeFragment` wiring
commit, then the `LinkResolverRepository` rewrite commit, then `3d0891f`
(Phase 3), then `778b098` (Phase 2 + 20-phase restructure), then `743ba46`
(Phase 1). If any of these are missing from `origin/master`, stop and don't
assume that phase's work is live in whatever environment you're auditing
from.

## Honest limitations of this session's work

- **Not compiled or run, and not verified against a live cobalt instance.**
  No Android SDK was available in this sandbox, and network egress here is
  restricted to a fixed domain allowlist (github.com, pypi.org, npmjs.com,
  etc.) with no route to `cobalt.tools` or any other self-hosted instance —
  so `LinkResolverRepository`'s request/response handling is structurally
  correct against the *documented* API contract (verified via web search
  against imputnet/cobalt's docs, not guessed) but has not actually round-
  tripped against a real server. **Build this and test it against a real
  cobalt instance before trusting it further** — this is the single most
  important thing to do first in Session 7, same standing caution as every
  prior handover about unverified-against-a-real-toolchain code.
- The API contract assumed is cobalt v7+-style (JSON POST, `status` field).
  Older instances predating that API rewrite use a different response
  shape entirely and are not supported — if a user-configured instance
  returns something this repository can't parse, they'll see "The cobalt
  instance returned an unreadable response." rather than a specific
  diagnosis of *why*.
- `filenameFromUrl()`'s extension/MIME-type guessing (used when a
  response omits `filename`) is best-effort from the URL path only — it
  doesn't read `Content-Type`/`Content-Disposition` response headers.
  Revisit if Phase 6 testing shows wrong filenames/MIME types often enough
  to matter.
- `resolveResult` is held in `HomeViewModel` but nothing renders it yet —
  by design, per Phase 4's scope — so end-to-end there is currently no
  visible difference to a user beyond the status message text changing
  from a fixed placeholder to a real (still text-only) outcome. Don't
  mistake the lack of a picker UI for a bug when starting Phase 6.
- Session was intentionally scoped to Phase 4 only; Phase 5
  (`ResolutionCacheEntity`) and Phase 6 (picker UI) are both still fully
  open, exactly as ARCHITECTURE.md describes.

## Immediate next steps

1. **Verify this session's commits landed** (see "Verify this landed"
   above). If `ARCHITECTURE.md` doesn't show Phase 4 marked done, stop and
   flag this — don't proceed as if the repo is further along than it
   actually is.
2. **Build the project.** Phases 2-4's code has not been run through a
   real Kotlin/Android toolchain since Session 5 first flagged this same
   gap — a clean `./gradlew assembleDebug` (or equivalent) still has not
   happened. Expect to find and fix compile errors, especially anything
   touching `HomeViewModel`'s constructor change (plain `ViewModel` →
   `AndroidViewModel`) if any other code references it directly instead of
   through `by viewModels()` (grepped for this at the end of Session 6 and
   found none, but re-check after any further edits).
3. **Test Phase 4 against a real cobalt instance** — paste a real link on
   the Home tab, confirm you see "Resolving…" then either a real format-
   count success message or a real, specific error. If the response shape
   doesn't match what `LinkResolverRepository` expects, that's the first
   thing to fix in Session 7, before Phase 5.
4. **Re-read `ARCHITECTURE.md` in full**, especially Phase 4's write-up and
   its "Known limitations" — treat it, not any prior session's summary
   (including this one), as the source of truth for what exists vs. what's
   genuinely new.
5. **Re-read `state.json` fresh** — same standing caution as every prior
   handover: don't trust any snapshot quoted in a document, including this
   one.
6. **Start Phase 5** (`ResolutionCacheEntity`/`ResolutionCacheDao`, added to
   the *existing* `DownloadDatabase.kt` — not a new database) once Phase
   4 is confirmed working against a real instance.
7. **Carried over from sessions 1-5, still not done:**
   - `AGENTS.md` truncation warning (80,689 chars vs 30,720 limit) — check
     if it's losing important instructions.
   - Deprecated `.env` setting `TERMINAL_CWD` should move to `config.yaml`
     under `terminal: cwd:`.
   - Strip leftover `[debug]` console.log lines from
     `/root/fallback-router/server.js`.
   - `Termux:Boot` still doesn't fire `BOOT_COMPLETED` on this device
     (known OEM/MIUI restriction); `.bashrc` resume-on-open trigger remains
     the working mitigation.
   - Provider health: re-check `~/router.log` before assuming anything
     about which provider is carrying real work — this hasn't been
     re-verified since session 3.

## Standing verification habits (still true, still not automated)

After any cycle or manual session:
- `cat ~/.hermes-pipeline/state.json` — did it advance correctly, is
  `last_updated` a real timestamp, does `current_phase` actually match
  what `ARCHITECTURE.md`'s Definition of Done says for the current repo
  state (not just what the model claimed)?
- `git log --oneline -5` — real commit, message matches actual diff.
- `git diff` on anything unstaged — no surprise edits.
- Read the actual file content for anything claiming "no stubs" —
  `grep TODO` alone is not sufficient, as Session 4 found with
  `ShortsViewModel.kt`, and as Session 5 found with the missing-imports bug
  that `grep TODO` also wouldn't have caught.
- Given this project's history (0/3 clean cycles before session 3's prompt
  rewrite, mixed since, and Phases 2-4's code all unverified against a real
  compiler as of this handover), don't assume unattended reliability from
  any single clean run — keep watching a few more before trusting it
  unsupervised.
