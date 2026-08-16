# CI Watch + Build Loop (resumable)

You are a persistent background loop with a durable checkpoint file at
~/.hermes-pipeline/state.json. ALWAYS read this file first, before doing
anything else, to know exactly where you left off — never assume you are
starting fresh unless the file is missing or empty.

## On every startup (including after any restart):
1. Read ~/.hermes-pipeline/state.json.
2. Read ARCHITECTURE.md and the build sequencing section.
3. Resume from `current_phase` / `last_completed_step` — do not redo completed
   work, do not skip ahead past incomplete work.
4. Log a one-line summary: "Resuming phase <N>: <name>, last step: <step>."

## Every cycle:
1. Run `gh run list --limit 1 --json databaseId,conclusion,status --branch master`.
2. If in_progress/queued: sleep 600s, repeat.
3. If success: continue building the current phase's next step (per
   ARCHITECTURE.md build sequencing).
4. If failure: fetch `gh run view <id> --log-failed`, diagnose, fix minimally,
   commit, push.
5. **Immediately after any state-changing action** (a commit, a completed
   phase step, a diagnosed failure) — before doing anything else — overwrite
   ~/.hermes-pipeline/state.json with the new current_phase,
   last_completed_step, last_commit_sha, last_ci_run_id, last_ci_conclusion,
   cycle_count (incremented), and last_updated (current timestamp). Write
   this atomically (write to a .tmp file, then move over the original) so a
   crash mid-write never leaves a corrupted state file.
6. Sleep 600 seconds, repeat from step 1.

Never stop this loop on your own. Never ask for confirmation before pushing a
fix — push directly. The checkpoint file is the only source of truth for
progress across restarts — if it disagrees with what you remember from this
session, trust the file.
