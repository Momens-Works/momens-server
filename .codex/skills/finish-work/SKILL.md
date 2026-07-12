---
name: finish-work
description: Use when finishing or completing tracked momens-server work after a PR merge, including requests to verify the merge, mark the Momens task done, or record the merged PR on the task.
---

# finish-work

Complete a Momens task only after independently verifying that its GitHub PR was merged.

## Workflow

1. Read `AGENTS.md` and `docs/rules/git.md`.
2. Inspect the current branch and worktree without modifying them.
3. Resolve the `MOM-<number>` label from the user's input or current branch, then fetch
   that task through the Momens MCP.
4. Resolve the associated PR from a supplied PR URL/number or the current branch. Use
   `gh pr view` to inspect at least `number`, `url`, `state`, `mergedAt`, `baseRefName`, and
   `headRefName`.
5. Require all of the following before changing Momens:
   - PR state is `MERGED`.
   - `mergedAt` is present.
   - The PR head branch carries the same Momens label.
   - The PR represents the task scope.
6. If verification fails, leave the task unchanged and report the exact reason. A PR that
   is open, approved, CI-green, or closed without merge is not complete.
7. If the task is not already `done`, update it to `done` through the Momens MCP.
8. Add one task comment with the merged PR URL and merge time unless the task already has
   an equivalent merge comment. Keep retries idempotent.

Do not delete branches, clean the worktree, or mark related tasks complete. Updating the
task and adding the merge comment are external writes authorized only by an explicit
request to finish the work.

## Output

Report the task label, final status, verified PR URL and merge time, whether a status
update/comment was needed, and any remaining follow-up.
