---
name: sync
description: Use before starting work in momens-server to synchronize project context, read the right repo docs, inspect git state, and identify unsettled decisions without inferring them.
---

# sync

Use this project-local skill before starting non-trivial work in `momens-server`.

The goal is to build enough context to act safely, not to summarize every document.
Do not duplicate project decisions into this skill. Treat repository docs as the source
of truth.

## Workflow

1. Confirm repository state:
   - `pwd`
   - `git status --short`
   - `git branch --show-current`
   - `git log --oneline -5`
2. Read entry documents:
   - `README.md`
   - `AGENTS.md`
   - `docs/README.md`
   - `docs/onboarding.md`
3. Read only the topic docs needed for the task:
   - Architecture: `docs/rules/architecture.md`
   - Git: `docs/rules/git.md`
   - Code conventions: `docs/rules/code-conventions.md`
   - Persistence: `docs/rules/persistence.md`
   - Configuration/secrets: `docs/rules/configuration.md`
   - Observability: `docs/rules/observability.md`
   - API contracts: `docs/spec/api-response-error-codes.md`
4. Classify the requested work:
   - project setup / docs / CI / convention
   - domain implementation
   - legacy migration
   - review / diff check
5. If the docs say a topic is unsettled or missing, do not decide silently. Ask the
   team or update the appropriate project doc.

## Output

Report briefly:

- current branch and worktree status
- documents read
- relevant settled rules
- unclear or missing decisions, if any
- recommended next step

Keep the response concise. Do not restate the full docs.
