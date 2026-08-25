#!/usr/bin/env bash
#
# prod-flyway-bootstrap.sh --verify 회귀 테스트.
#
# 두 가지를 지킨다.
#
# 1. loopback 치환이 authority 의 host 자리에만 적용된다. 이 변환이 잘못되면 원격 DB 주소를
#    바꿔 엉뚱한 대상을 검증하거나, 검증 대상에 접속하지 못한다.
# 2. psql 이 부분 출력 뒤 실패했을 때 그 결과를 정상 조회로 다루지 않는다. 심기 목록은 별도
#    검사(unverified_seeds)가 지키지만, 끊긴 조회를 성공으로 보고하면 진단이 어긋난다.
# 3. --generate 의 생성물이 이력 테이블 소유권을 momens_server 로 넘긴다. 이 줄이 빠지면 심기는
#    성공하고 다음 기동이 permission denied 로 죽는다 — 심기 시점에는 아무 신호가 없다.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "$repo_root/scripts/prod-flyway-bootstrap.sh"

failures=0

expect_url() {
    local input="$1" want="$2" got
    got="$(rewrite_loopback_host "$input")"
    if [[ "$got" != "$want" ]]; then
        failures=$((failures + 1))
        printf 'FAIL  %s\n  기대: %s\n  실제: %s\n' "$input" "$want" "$got" >&2
    fi
}

# --- loopback 은 host 자리에서만 바뀐다 ---
expect_url 'postgresql://momens@127.0.0.1:15498/verifydb' \
           'postgresql://momens@host.docker.internal:15498/verifydb'
expect_url 'postgresql://momens@localhost:5432/prod' \
           'postgresql://momens@host.docker.internal:5432/prod'

# 포트도 경로도 없는 형식
expect_url 'postgresql://localhost' 'postgresql://host.docker.internal'
expect_url 'postgresql://localhost?sslmode=disable' \
           'postgresql://host.docker.internal?sslmode=disable'

# IPv6 loopback
expect_url 'postgresql://momens@[::1]:5432/db' \
           'postgresql://momens@host.docker.internal:5432/db'

# --- 바뀌면 안 되는 것들 ---
# host 가 loopback 을 부분 문자열로만 포함한다
expect_url 'postgresql://momens@notlocalhost:5432/prod' \
           'postgresql://momens@notlocalhost:5432/prod'
# DB 이름이 loopback 과 같다
expect_url 'postgresql://momens@localhost:5432/localhost' \
           'postgresql://momens@host.docker.internal:5432/localhost'
# 쿼리 값에 loopback 이 들어 있다
expect_url 'postgresql://momens@db.example.com:5432/prod?application_name=localhost:review' \
           'postgresql://momens@db.example.com:5432/prod?application_name=localhost:review'
# 실제 prod 형태의 원격 URL
expect_url 'postgresql://momens_server@db.abc.supabase.co:5432/postgres?sslmode=require' \
           'postgresql://momens_server@db.abc.supabase.co:5432/postgres?sslmode=require'
# 비밀번호에 @ 가 있는 userinfo
expect_url 'postgresql://user:p@ss@localhost:5432/db' \
           'postgresql://user:p@ss@host.docker.internal:5432/db'
# 키워드 형식은 손대지 않는다
expect_url 'host=localhost port=5432 dbname=db' 'host=localhost port=5432 dbname=db'

# --- 부분 출력 뒤 실패한 조회를 성공으로 다루지 않는다 ---
fake_psql_partial() {
    printf '20260624090000|111\n'
    echo "server closed the connection unexpectedly" >&2
    return 2
}

partial_out="$(mktemp)"
trap 'rm -f "$partial_out"' EXIT

if PSQL_RUNNER=fake_psql_partial PGPASSWORD=x \
        verify 'postgresql://momens@127.0.0.1:1/none' > "$partial_out" 2>&1; then
    failures=$((failures + 1))
    echo "FAIL  부분 출력 뒤 실패한 조회가 성공으로 판정됐습니다." >&2
elif ! grep -q "부분 결과는 쓰지 않습니다" "$partial_out"; then
    failures=$((failures + 1))
    echo "FAIL  psql 실패를 알리는 메시지가 없습니다." >&2
    sed 's/^/  /' "$partial_out" >&2
fi

# --- 생성물이 이력 테이블 소유권을 넘긴다 ---
# 이력 테이블을 만든 role 이 소유자가 된다. 실행 창구인 Supabase SQL Editor 는 postgres 세션이라,
# 이 줄이 빠지면 momens_server 가 이력 테이블에 아무 권한도 갖지 못하고 다음 기동이
# permission denied 로 죽는다. 절차 문서에만 적으면 빠지는 유형이라 생성물에 넣었고, 빠졌는지를
# 여기서 본다.
preamble="$(bootstrap_ddl_preamble)"

if ! grep -q '^ALTER TABLE flyway_schema_history OWNER TO momens_server;$' <<<"$preamble"; then
    failures=$((failures + 1))
    echo "FAIL  생성물에 이력 테이블 소유권 이전이 없습니다." >&2
fi

# 트랜잭션 밖으로 나가면 앞의 INSERT 만 롤백되고 소유권만 남는 상태가 가능하다.
if ! awk '/^BEGIN;$/ { inside = 1 } /^ALTER TABLE flyway_schema_history OWNER TO/ { found = inside }
          END { exit !found }' <<<"$preamble"; then
    failures=$((failures + 1))
    echo "FAIL  소유권 이전이 BEGIN 안에 있지 않습니다." >&2
fi

# 소유권 이전은 CREATE TABLE 뒤여야 한다.
if ! awk '/^CREATE TABLE IF NOT EXISTS flyway_schema_history/ { created = 1 }
          /^ALTER TABLE flyway_schema_history OWNER TO/ { exit !created }' <<<"$preamble"; then
    failures=$((failures + 1))
    echo "FAIL  소유권 이전이 CREATE TABLE 보다 앞에 있습니다." >&2
fi

if [[ "$failures" -ne 0 ]]; then
    echo "prod-flyway-bootstrap --verify 회귀 테스트 실패 $failures 건" >&2
    exit 1
fi

echo "prod-flyway-bootstrap --verify 회귀 테스트 OK"
