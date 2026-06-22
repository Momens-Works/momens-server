---
name: diff-review
description: Use before opening or updating a PR in momens-server to review the current branch diff against develop or main, checking formatting, conventions, docs, secrets, tests, and API contract compatibility.
---

# diff-review

Use this project-local skill before opening a PR or when asked to review the current
branch against `develop` or `main`.

Default base branch: `origin/develop`.
Use `origin/main` only when the user specifically asks for release/main comparison.

## Workflow

1. Read:
   - `AGENTS.md`
   - `docs/rules/git.md`
   - `docs/rules/code-conventions.md`
   - `docs/spec/api-response-error-codes.md` if API behavior changed
2. Run the helper script:

   ```bash
   bash .codex/skills/diff-review/scripts/check_diff.sh origin/develop
   ```

   Use `origin/main` instead of `origin/develop` if requested.
3. Inspect the actual diff:

   ```bash
   git diff --stat origin/develop...HEAD
   git diff --name-status origin/develop...HEAD
   git diff origin/develop...HEAD
   ```

4. Review for:
   - branch and commit convention drift
   - missing docs updates
   - unsettled decisions that were silently resolved
   - secret or local-only file leakage
   - API response/error body compatibility
   - unnecessary refactors or unrelated churn
   - missing or insufficient tests

## Validation Commands

Prefer this order:

```bash
./gradlew spotlessCheck
./gradlew test
./gradlew bootJar
```

If the environment blocks Gradle cache or dependency access, rerun with an appropriate
project-local `GRADLE_USER_HOME` or request approval for network access.

## Output

Use review style:

1. Findings first, ordered by severity, with file/line references when possible.
2. Then test/validation results.
3. Then residual risks or follow-up suggestions.

If there are no issues, say so clearly and still report validation coverage.
