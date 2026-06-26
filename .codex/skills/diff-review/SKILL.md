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
   git log --oneline origin/develop..HEAD
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

## Momens-Specific Codex Checks

These checks are Codex-only process guardrails. Keep them in this `.codex` skill and
do not copy them into shared human docs or `.claude` skills unless the team explicitly
decides to make the rule agent-agnostic.

### Transactional side effects before exceptions

For any service method annotated with `@Transactional`:

- Search for DB state changes before `throw new BusinessException(...)` or any other
  runtime exception.
- If the state change is a security, auth/session, audit, or policy side effect that
  must survive the failed request, verify that default transaction rollback will not
  undo it.
- Require one of these before approving the diff:
  - a separate `REQUIRES_NEW` transaction,
  - explicit `noRollbackFor`,
  - a split transaction boundary,
  - or a documented reason why rollback is intended.
- For auth/session/security flows, prefer a service-level integration test with the
  real JPA adapter. In-memory port fakes do not verify lock, flush, or rollback
  semantics.

### Ticket boundary hygiene

Before creating or updating a PR:

- Map each meaningful diff group to the requested Linear issue.
- If another issue's deliverable appears in the diff, call it out before creating the
  PR.
- Do not silently absorb another ticket's scope just because it is technically
  adjacent.
- If the extra work should stay, mention the related issue in the PR body or Linear
  comments so ticket status remains explicit.

### Commit convention check

Before creating or updating a PR, inspect:

```bash
git log --oneline origin/develop..HEAD
```

Verify commit messages follow the project format from `docs/rules/git.md`:

```text
<type> (<domain>): <message>
```

For example:

```text
feat (auth): 모바일 Google 로그인 구현
refactor (presentation): DTO 패키지 정리
```

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
