---
name: start-work
description: Use when starting tracked work in momens-server, including requests to begin implementation, create or reuse a Momens task, mark it in progress, or create a task-labelled Git branch.
---

# start-work

Start one focused piece of tracked work from a Momens task and leave the repository on
the correctly named branch.

## Workflow

1. Read `AGENTS.md`, `docs/rules/git.md`, and `docs/onboarding.md`.
2. Inspect `git status --short`, the current branch, and recent commits. Preserve all
   existing worktree changes.
   - Compare `develop` with `origin/develop` before creating a branch.
   - If local `develop` is behind, stop and report the base update required. Do not create
     a task branch from a stale base or implicitly pull, stash, or rebase existing work.
3. Confirm the work title, context, scope, completion criteria, and target Momens project.
   Use milestone, assignee, priority, and due date only when already confirmed.
4. Use the Momens MCP to list project tasks and check for an exact or clearly equivalent
   task before creating anything.
   - Reuse one unambiguous match.
   - Ask before choosing between multiple plausible matches.
   - Do not silently reopen a `done` or `cancelled` task.
5. If no match exists, create one task with the confirmed context, scope, and completion
   criteria. Because this skill means work is starting, create it as `in_progress`.
   If a reused task is `backlog` or `todo`, update it to `in_progress`.
6. If the requested milestone cannot be assigned or the task URL is not returned through
   the MCP, report the corresponding manual assignment or URL-copy step explicitly. Do not
   claim either value was resolved.
7. Create or reuse the Git branch:
   - Format: `<MOM-label>-<type>/<kebab-description>`.
   - Allowed types: `feat`, `fix`, `docs`, `refactor`, `chore`.
   - Start from `develop` for normal work.
   - If already on the matching task branch, reuse it.
   - If on a different task branch, stop instead of switching silently.
   - Do not pull, stash, discard, or rewrite existing changes implicitly.

Task creation, status updates, and branch creation are authorized only when the user has
explicitly asked to start or track work.

## Output

Report the Momens label, title, status, project, whether it was reused or created, the
current branch, task URL when available, and any manual milestone assignment or URL-copy
step or base-branch update still required.
