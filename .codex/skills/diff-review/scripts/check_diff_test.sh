#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_REPO="$(mktemp -d "${TMPDIR:-/tmp}/diff-review-test.XXXXXX")"
trap 'rm -rf -- "${TEST_REPO}"' EXIT

git -C "${TEST_REPO}" init -q
git -C "${TEST_REPO}" config user.name "Diff Review Test"
git -C "${TEST_REPO}" config user.email "diff-review-test@example.com"
git -C "${TEST_REPO}" commit -q --allow-empty -m "test base"

{
  printf '%s\n' \
    'String csrfToken = csrfTokenRepository.load(request);' \
    '// CSRF token: SameSite cookie policy'
  printf '%s = "%s"\n' 'token' 'hardcoded-secret'
  printf '%s=%s\n' 'DATABASE_PASSWORD' 'momens'
  printf '%s=%s\n' 'MOMENS_AUTH_JWT_SECRET' 'change-me'
  printf '%s=%s\n' 'MOMENS_AUTH_GOOGLE_CLIENT_SECRET' 'change-me'
  printf '%s=%s\n' 'POSTGRES_PASSWORD' 'momens'
  printf '%s: %s\n' 'jwt-secret' 'test-only-abc'
  printf '%s: %s\n' 'client-secret' 'test-web-client-secret'
  printf '%s%s\n' '-----BEGIN PRIVATE' ' KEY-----'
  printf '%s%s\n' '-----BEGIN ENCRYPTED' ' PRIVATE KEY-----'
} >"${TEST_REPO}/fixture.txt"
git -C "${TEST_REPO}" add fixture.txt

OUTPUT="$(cd "${TEST_REPO}" && bash "${SCRIPT_DIR}/check_diff.sh" HEAD)"

if grep -Fq 'csrfToken = csrfTokenRepository.load(request)' <<<"${OUTPUT}"; then
  echo "error: csrfToken runtime assignment was reported as a secret" >&2
  exit 1
fi

if grep -Fq 'CSRF token: SameSite cookie policy' <<<"${OUTPUT}"; then
  echo "error: CSRF documentation was reported as a secret" >&2
  exit 1
fi

EXPECTED_LINE=$(printf '%s = "%s"' 'token' 'hardcoded-secret')
if ! grep -Fq "${EXPECTED_LINE}" <<<"${OUTPUT}"; then
  echo "error: hardcoded token was not reported" >&2
  exit 1
fi

for EXPECTED_LINE in \
  "$(printf '%s=%s' 'DATABASE_PASSWORD' 'momens')" \
  "$(printf '%s=%s' 'MOMENS_AUTH_JWT_SECRET' 'change-me')" \
  "$(printf '%s=%s' 'MOMENS_AUTH_GOOGLE_CLIENT_SECRET' 'change-me')" \
  "$(printf '%s=%s' 'POSTGRES_PASSWORD' 'momens')" \
  "$(printf '%s: %s' 'jwt-secret' 'test-only-abc')" \
  "$(printf '%s: %s' 'client-secret' 'test-web-client-secret')"; do
  if ! grep -Fq "${EXPECTED_LINE}" <<<"${OUTPUT}"; then
    echo "error: config secret was not reported: ${EXPECTED_LINE}" >&2
    exit 1
  fi
done

EXPECTED_LINE=$(printf '%s%s' '-----BEGIN PRIVATE' ' KEY-----')
if ! grep -Fq -- "${EXPECTED_LINE}" <<<"${OUTPUT}"; then
  echo "error: private key header was not reported" >&2
  exit 1
fi

EXPECTED_LINE=$(printf '%s%s' '-----BEGIN ENCRYPTED' ' PRIVATE KEY-----')
if ! grep -Fq -- "${EXPECTED_LINE}" <<<"${OUTPUT}"; then
  echo "error: encrypted private key header was not reported" >&2
  exit 1
fi

echo "diff-review sensitive-line checks passed"
