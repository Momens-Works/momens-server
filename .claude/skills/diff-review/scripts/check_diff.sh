#!/usr/bin/env bash
set -euo pipefail

BASE="${1:-origin/develop}"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "error: not inside a git repository" >&2
  exit 1
fi

if ! git rev-parse --verify "${BASE}" >/dev/null 2>&1; then
  echo "error: base ref '${BASE}' not found" >&2
  echo "hint: fetch the branch or pass another base ref, e.g. origin/main" >&2
  exit 1
fi

echo "== Branch =="
git branch --show-current

echo
echo "== Worktree =="
git status --short

echo
echo "== Diff stat (${BASE}...HEAD) =="
git diff --stat "${BASE}...HEAD"

echo
echo "== Changed files =="
git diff --name-status "${BASE}...HEAD"

echo
echo "== Untracked sensitive-looking files =="
git status --short --ignored --untracked-files=all \
  | awk '/^(\?\?|!!)/ {print substr($0, 4)}' \
  | grep -E '(^|/)(\.env|application-.*secret\.ya?ml|.*\.pem|.*\.key|.*secret.*|.*credential.*)$' || true

echo
echo "== Sensitive-looking diff lines =="
git diff "${BASE}...HEAD" -- \
  ':!gradle/wrapper/gradle-wrapper.jar' \
  | grep -nEi '(^\+.*"?(password|passwd|secret|token|api[_-]?key|private[_-]?key|credential)"?[[:space:]]*(:[[:space:]]*[^[:space:]:].*|=[^=].*))|(^\+.*BEGIN ((RSA|OPENSSH|EC) )?PRIVATE KEY)' || true

echo
echo "== Unsettled decision markers in diff =="
git diff "${BASE}...HEAD" | grep -nE '미정|보류|TBD|TODO|unsettled|not decided' || true
