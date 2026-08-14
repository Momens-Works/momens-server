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

MERGE_BASE="$(git merge-base "${BASE}" HEAD)"

echo "== Branch =="
git branch --show-current

echo
echo "== Worktree =="
git status --short

echo
echo "== Base divergence (${BASE}...HEAD; base-only head-only) =="
git rev-list --left-right --count "${BASE}...HEAD"

echo
echo "== Diff stat (${MERGE_BASE}..worktree) =="
git diff --stat "${MERGE_BASE}"

echo
echo "== Changed files =="
git diff --name-status "${MERGE_BASE}"

echo
echo "== Untracked sensitive-looking files =="
git status --short --ignored --untracked-files=all \
  | awk '/^(\?\?|!!)/ {print substr($0, 4)}' \
  | grep -E '(^|/)(\.env|application-.*secret\.ya?ml|.*\.pem|.*\.key|.*secret.*|.*credential.*)$' || true

echo
echo "== Sensitive-looking diff lines =="
SENSITIVE_KEYS='password|passwd|secret|token|api[_-]?key|private[_-]?key|credential'
SENSITIVE_CONFIG_KEY="([[:alnum:]]+[._-])*(${SENSITIVE_KEYS})([._-][[:alnum:]]+)*"
SENSITIVE_LINE_PATTERN="(^\\+[[:space:]]*\"?${SENSITIVE_CONFIG_KEY}\"?[[:space:]]*(:[[:space:]]*[^[:space:]:].*|=[^=].*))|(^\\+.*(${SENSITIVE_KEYS})[[:alnum:]_-]*[[:space:]]*=[[:space:]]*['\"][^'\"]+['\"])|(^\\+.*BEGIN ((RSA|OPENSSH|EC|ENCRYPTED) )?PRIVATE KEY)"
git diff "${MERGE_BASE}" -- \
  ':!gradle/wrapper/gradle-wrapper.jar' \
  | grep -nEi "${SENSITIVE_LINE_PATTERN}" || true

echo
echo "== Unsettled decision markers in diff =="
git diff "${MERGE_BASE}" | grep -nE '미정|보류|TBD|TODO|unsettled|not decided' || true
