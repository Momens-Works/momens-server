#!/usr/bin/env bash
#
# prod 소유권 이전 스크립트 (MOM-0909).
#
# ADR-0019 의 주도권 이전을 소유권 수준에서 완성한다. `ALTER TABLE` 은 GRANT 체계 밖의 소유자
# 권한이라 어떤 권한 조합으로도 얻을 수 없다 — `GRANT ALL PRIVILEGES` 로도 `must be owner of
# table` 이 난다. 서버가 마이그레이션 파일을 갖는 레거시 테이블의 소유권을 한 번에 넘긴다.
#
# **소유권 이전은 이전 소유자의 권한을 함께 가져간다.** postgres 를 grantee 로 하는 ACL 항목이
# 새 소유자로 옮겨가기 때문이다. 지금 실제 트래픽을 받는 것은 레거시(postgres 로 접속)이므로
# 재발급 누락은 곧 레거시 장애다. 그래서 이전과 재발급을 짝으로 묶고 전체를 한 트랜잭션에 담는다 —
# 중간에 죽으면 통째로 롤백된다.
#
# 실행 창구는 Supabase SQL Editor(= postgres 세션)다. 비-superuser 가
# `ALTER TABLE ... OWNER TO X` 를 하려면 X 로 SET ROLE 할 수 있어야 하므로
# `GRANT momens_server TO postgres WITH SET TRUE` 가 선행한다.
#
# 사용법:
#   prod-ownership-transfer.sh --generate [출력파일]   이전 SQL 을 만든다
#   prod-ownership-transfer.sh --check                 매니페스트가 리포와 어긋나지 않는지 본다

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
manifest="$repo_root/scripts/prod-ownership-transfer-tables.txt"
seed_manifest="$repo_root/scripts/prod-flyway-bootstrap-seed.txt"
exec_manifest="$repo_root/scripts/prod-flyway-bootstrap-exec.txt"

# prod 에서 앱이 접속하는 role 과 운영 창구 role.
prod_role="momens_server"
owner_role="postgres"

tables() { grep -vE '^\s*(#|$)' "$manifest"; }

# 마이그레이션 목록이 CREATE TABLE 하는 대상을 뽑는다.
created_by() {
    local list="$1" version file
    while read -r version; do
        file="$(find "$repo_root/modules" "$repo_root/app" -name "V${version}__*.sql" \
                     -not -path '*/build/*' 2>/dev/null | head -1)"
        [[ -n "$file" ]] || continue
        # 주석을 먼저 걷는다. 마이그레이션 주석이 `CREATE TABLE IF NOT EXISTS` 를 문장으로
        # 언급하는 경우가 있어 그대로 두면 그것까지 잡힌다.
        # `IF NOT EXISTS` 도 지운다. 남겨 두면 grep -E 가 선택적 그룹을 건너뛰고 `IF` 를 테이블
        # 이름으로 잡는다.
        sed -E -e 's/--.*$//' \
               -e 's/[Ii][Ff][[:space:]]+[Nn][Oo][Tt][[:space:]]+[Ee][Xx][Ii][Ss][Tt][Ss][[:space:]]+//g' "$file" \
            | grep -ioE 'CREATE TABLE[[:space:]]+[a-z_]+' \
            | awk '{print tolower($NF)}'
    done < <(grep -oE '^[0-9]+' "$list") | sort -u
}

# 매니페스트가 리포와 어긋나면 prod 에서만 드러난다. 심기 목록이 만드는 테이블 중 실행 집합도
# 만드는 것(= prod 에 실물이 없는 것)과 task_roles 를 뺀 나머지가 이전 대상이어야 한다.
check() {
    local expected actual missing extra
    # 실행 집합이 만드는 것은 prod 에 실물이 없어 이전 대상이 아니다. task_roles 는 prod 에
    # 존재한 적이 없다(설계 2.8).
    expected="$(comm -23 <(created_by "$seed_manifest") \
                         <({ created_by "$exec_manifest"; echo task_roles; } | sort -u))"
    actual="$(tables | sort)"

    missing="$(comm -23 <(printf '%s\n' "$expected") <(printf '%s\n' "$actual"))"
    extra="$(comm -13 <(printf '%s\n' "$expected") <(printf '%s\n' "$actual"))"

    if [[ -n "$missing" || -n "$extra" ]]; then
        [[ -n "$missing" ]] && { echo "매니페스트에 빠진 테이블:" >&2; sed 's/^/  /' <<<"$missing" >&2; }
        [[ -n "$extra" ]] && { echo "리포가 파일을 갖지 않는데 매니페스트에 있는 테이블:" >&2; sed 's/^/  /' <<<"$extra" >&2; }
        return 1
    fi
    echo "소유권 이전 대상 OK ($(printf '%s\n' "$actual" | grep -c .)건)"
}

generate() {
    local output="${1:-}" list count
    list="$(tables)"
    count="$(printf '%s\n' "$list" | grep -c .)"
    [[ "$count" -gt 0 ]] || { echo "이전 대상이 비어 있습니다: $manifest" >&2; return 1; }

    {
        echo "-- prod 소유권 이전 (MOM-0909)"
        echo "-- 생성: $(date -u +%Y-%m-%dT%H:%M:%SZ) · 기준 커밋: $(git -C "$repo_root" rev-parse HEAD)"
        echo "-- 대상 ${count}건. 근거는 scripts/prod-ownership-transfer-tables.txt 에 있다."
        echo "--"
        echo "-- 선행 조건: GRANT $prod_role TO $owner_role WITH SET TRUE;"
        echo "--   비-superuser 세션이 ALTER TABLE ... OWNER TO 를 하려면 대상 role 로 SET ROLE 할"
        echo "--   수 있어야 한다. 없으면 must be able to SET ROLE 로 죽는다."
        echo ""
        echo "BEGIN;"
        echo ""
        echo "SET LOCAL search_path = public;"
        echo ""
        echo "DO \$\$"
        echo "DECLARE"
        echo "    t text;"
        echo "    absent text := '';"
        echo "BEGIN"
        echo "    FOREACH t IN ARRAY ARRAY["
        printf '        %s\n' "$(printf "'%s'," $list | sed 's/,$//')"
        echo "    ] LOOP"
        echo "        IF NOT EXISTS (SELECT 1 FROM pg_tables"
        echo "                        WHERE schemaname = 'public' AND tablename = t) THEN"
        echo "            absent := absent || ' ' || t;"
        echo "            CONTINUE;"
        echo "        END IF;"
        echo ""
        echo "        EXECUTE format('ALTER TABLE public.%I OWNER TO $prod_role', t);"
        echo "        -- 위 한 줄이 $owner_role 의 권한을 가져간다. 레거시가 그 role 로 접속하므로"
        echo "        -- 재발급이 반드시 짝으로 붙어야 한다."
        echo "        EXECUTE format("
        echo "            'GRANT SELECT, INSERT, UPDATE, DELETE ON public.%I TO $owner_role', t);"
        echo "    END LOOP;"
        echo ""
        echo "    -- 없는 테이블은 건너뛰되 조용히 넘기지 않는다. 매니페스트가 실제와 어긋났다는"
        echo "    -- 신호이고, 그대로 두면 그 테이블은 영원히 레거시 소유로 남는다."
        echo "    IF absent <> '' THEN"
        echo "        RAISE WARNING '대상에 없는 테이블을 건너뛰었습니다:%', absent;"
        echo "    END IF;"
        echo "END \$\$;"
        echo ""
        echo "-- 결과 확인. 아래가 ${count}건이어야 한다."
        echo "SELECT count(*) AS owned_by_$prod_role"
        echo "  FROM pg_tables WHERE schemaname = 'public' AND tableowner = '$prod_role';"
        echo ""
        echo "-- 레거시가 전부 읽고 쓸 수 있는지. 아래가 0건이어야 한다."
        echo "SELECT tablename FROM pg_tables"
        echo " WHERE schemaname = 'public' AND tableowner = '$prod_role'"
        echo "   AND NOT has_table_privilege('$owner_role', schemaname || '.' || tablename, 'SELECT');"
        echo ""
        echo "COMMIT;"
    } > "${output:-/dev/stdout}"

    [[ -n "$output" ]] && echo "생성했습니다: $output (${count}건)" >&2
    return 0
}

case "${1:-}" in
    --generate) generate "${2:-}" ;;
    --check)    check ;;
    *) echo "사용법: $0 --generate [출력파일] | --check" >&2; exit 2 ;;
esac
