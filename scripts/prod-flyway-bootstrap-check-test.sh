#!/usr/bin/env bash
#
# prod-flyway-bootstrap-check.sh 회귀 테스트.
#
# 그 스크립트는 CI 필수 체크에 실린 게이트다. 게이트 자체가 검증되지 않으면 조용히 통과하는
# 게이트가 된다. 자매 스크립트(prod-readiness-ledger-test.sh)와 같은 위치를 차지한다.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
check="$repo_root/scripts/prod-flyway-bootstrap-check.sh"

fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

mkdir -p "$fixture/scripts" "$fixture/modules/a/src/main/resources/db/migration"
cp "$check" "$fixture/scripts/"

migration() {
    : > "$fixture/modules/a/src/main/resources/db/migration/$1"
}

write_lists() {
    printf '# seed\n%s\n' "$1" > "$fixture/scripts/prod-flyway-bootstrap-seed.txt"
    printf '# exec\n%s\n' "$2" > "$fixture/scripts/prod-flyway-bootstrap-exec.txt"
}

run_check() { (cd "$fixture" && ./scripts/prod-flyway-bootstrap-check.sh >/dev/null 2>&1); }

expect_pass() {
    run_check || { echo "실패해서는 안 되는 경우가 실패했습니다: $1" >&2; exit 1; }
}
expect_fail() {
    ! run_check || { echo "잡아야 하는 경우를 통과시켰습니다: $1" >&2; exit 1; }
}

migration "V20260101000000__one.sql"
migration "V20260102000000__two.sql"

write_lists "20260101000000  # seed" "20260102000000  # exec"
expect_pass "정상 분류"

# 실제로 일어났던 실패다 — 새 미러가 목록 없이 머지됐다.
migration "V20260103000000__three.sql"
expect_fail "미분류 마이그레이션"

write_lists "20260101000000
20260103000000" "20260102000000"
expect_pass "미분류 해소"

write_lists "20260101000000
20260102000000
20260103000000" "20260102000000"
expect_fail "심기와 실행 양쪽에 있음"

write_lists "20260101000000
20260103000000
20260199000000" "20260102000000"
expect_fail "목록에 있으나 파일이 없음"

write_lists "20260101000000
20260103000000" "20260102000000"
migration "not-a-migration.sql"
expect_fail "파일명 규약 위반"
rm "$fixture/modules/a/src/main/resources/db/migration/not-a-migration.sql"
expect_pass "규약 위반 해소"

rm "$fixture/scripts/prod-flyway-bootstrap-exec.txt"
expect_fail "분류 목록 파일 없음"

echo "prod-flyway-bootstrap-check 회귀 테스트 OK"
