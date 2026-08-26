#!/usr/bin/env bash
#
# prod Flyway 부트스트랩 스크립트 (MOM-0909).
#
# prod 에서 서버 Flyway 를 처음 켤 때, 레거시가 이미 만든 객체의 마이그레이션이 재실행돼
# `relation already exists` 로 죽지 않도록 `flyway_schema_history` 에 적용 기록을 미리 심는다.
# 이 스크립트는 그 INSERT 문을 만든다. 적용은 사람이 한다.
#
# 생성물은 이력 테이블에 momens_server 의 DML 을 부여하는 줄을 포함한다. 절차 문서에만 적으면
# 빠지는 유형이라 생성물이 직접 갖게 했다.
#
# 체크섬을 직접 계산하지 않는다. 빈 스크래치 DB 에 Flyway 를 그대로 돌려 나온 값을 복사한다.
# Flyway 는 매 기동 시 파일을 다시 해싱해 이 값과 대조하고 다르면 checksum mismatch 로 기동을
# 거부하므로, CRC32 를 재구현하면 어긋났을 때 원인 추적이 어렵다.
#
# 앱은 flyway-core 를 라이브러리로 쓰고 이 스크립트는 같은 버전의 CLI 이미지를 쓴다. 두 경로가
# 같은 체크섬을 내는지는 --verify 로 확인한다(설계 문서 4절 참조).
#
# 사용법:
#   prod-flyway-bootstrap.sh --generate [출력파일]   심을 INSERT 문을 만든다
#   PGPASSWORD=... prod-flyway-bootstrap.sh --verify <libpq URL>
#                                                   기존 이력과 체크섬이 같은지 대조한다
#
# --verify 의 대상은 **앱의 Flyway 가 만든 이력**이어야 한다. 스크래치 DB 를 CLI 로 만들어 대조하면
# CLI 끼리 비교하는 순환이다. scripts/legacy-diff 의 server-db 가 bootJar 로 만들어지므로 그것을
# 릴리스 대상 커밋으로 띄워 쓴다. dev 는 main 릴리스로만 전진해 최신 마이그레이션이 없다.
#
# **산출 기준은 실제로 배포되는 커밋이다.** 미러성 파일은 계속 늘고 있으므로, 릴리스 대상 커밋을
# 체크아웃한 상태에서 실행하고 산출부터 INSERT 까지 그 커밋을 바꾸지 않는다.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
seed_manifest="$repo_root/scripts/prod-flyway-bootstrap-seed.txt"

# 앱이 쓰는 flyway-core 와 같은 버전이어야 한다. libs.versions.toml 에는 flyway 항목이 없다 —
# spring-boot-starter-flyway 의 BOM 이 버전을 정하므로 Spring Boot 를 올리면 여기가 조용히 어긋난다.
# 확인:
#   ./gradlew -q :app:dependencies --configuration runtimeClasspath | grep flyway-core
flyway_version="12.4.0"

# prod 에서 앱이 접속하는 DB role. 생성물이 이력 테이블의 소유권을 이 role 로 넘긴다 — 이유는
# 아래 OWNER TO 줄의 주석에 있다. 값이 바뀌면 여기만 고친다.
prod_role="momens_server"

scratch_container="momens-flyway-bootstrap-scratch"
scratch_db="scratch"
scratch_user="momens"
scratch_password="momens"

cleanup() {
    docker rm -f "$scratch_container" >/dev/null 2>&1 || true
    [[ -n "${work_dir:-}" ]] && rm -rf "$work_dir"
}

seed_versions() {
    grep -oE '^[0-9]+' "$seed_manifest" | sort -u
}

# 저장소 전체의 마이그레이션을 한 디렉터리로 모은다. 앱은 여러 모듈을 하나의 클래스패스로 합쳐
# 읽으므로, 파일 집합이 같으면 체크섬도 같다.
collect_migrations() {
    local target="$1" count
    mkdir -p "$target"
    find "$repo_root" -type d \( -name build -o -name .git -o -name .gradle-work \) -prune -o \
        -path '*/src/main/resources/db/migration/V*.sql' -print \
        | while IFS= read -r file; do cp "$file" "$target/"; done
    local found
    found="$(find "$repo_root" -type d \( -name build -o -name .git -o -name .gradle-work \) -prune -o \
        -path '*/src/main/resources/db/migration/V*.sql' -print | wc -l | tr -d ' ')"
    count="$(find "$target" -name 'V*.sql' | wc -l | tr -d ' ')"
    [[ "$count" -gt 0 ]] || { echo "마이그레이션을 찾지 못했습니다." >&2; return 1; }
    # basename 으로 평탄화하므로 모듈 간 파일명이 겹치면 조용히 덮어쓴다. 버전이 같으면 Flyway 가
    # 죽지만 이름만 같은 경우는 드러나지 않으므로 개수로 잡는다.
    [[ "$count" -eq "$found" ]] || {
        echo "마이그레이션 수집에서 파일이 유실됐습니다: 리포 $found 건, 수집 $count 건." >&2
        echo "모듈 간 파일명이 겹칩니다." >&2
        return 1
    }
    echo "$count"
}

start_scratch_db() {
    docker rm -f "$scratch_container" >/dev/null 2>&1 || true
    docker run -d --name "$scratch_container" \
        -e POSTGRES_DB="$scratch_db" \
        -e POSTGRES_USER="$scratch_user" \
        -e POSTGRES_PASSWORD="$scratch_password" \
        pgvector/pgvector:pg16 >/dev/null
    local i
    for i in $(seq 1 60); do
        docker exec "$scratch_container" pg_isready -U "$scratch_user" -d "$scratch_db" >/dev/null 2>&1 && return 0
        sleep 1
    done
    echo "스크래치 DB 기동에 실패했습니다." >&2
    return 1
}

run_flyway_migrate() {
    local sql_dir="$1"
    docker run --rm --network "container:$scratch_container" \
        -v "$sql_dir:/flyway/sql:ro" \
        "flyway/flyway:$flyway_version" \
        -url="jdbc:postgresql://localhost:5432/$scratch_db" \
        -user="$scratch_user" -password="$scratch_password" \
        -locations=filesystem:/flyway/sql \
        migrate >/dev/null
}

scratch_query() {
    docker exec "$scratch_container" psql -U "$scratch_user" -d "$scratch_db" -Atc "$1"
}

# 생성물의 DDL 머리말. INSERT 앞에 오는 전부다. 회귀 테스트가 이 함수만 따로 부를 수 있도록
# generate 에서 분리해 뒀다 — 머리말을 확인하려고 docker 와 스크래치 DB 를 띄우게 하면 테스트가
# 무거워져 결국 돌지 않는다.
bootstrap_ddl_preamble() {
    echo "-- prod flyway_schema_history 부트스트랩 (MOM-0909)"
    echo "-- 생성: $(date -u +%Y-%m-%dT%H:%M:%SZ) · 기준 커밋: $(git -C "$repo_root" rev-parse HEAD)"
    echo "-- Flyway CLI $flyway_version 이 빈 스크래치 DB 에서 계산한 체크섬이다."
    echo "--"
    echo "-- 적용 전 확인: 이 DB 에 flyway_schema_history 가 없어야 한다."
    echo "-- 실패하면 DROP TABLE flyway_schema_history 로 완전히 되돌릴 수 있다(설계 7절)."
    echo ""
    echo "BEGIN;"
    echo ""
    echo "-- 대상 스키마를 고정한다. 운영자 세션의 search_path 가 public 이 아니면 엉뚱한"
    echo "-- 스키마에 이력이 생기고, 실패가 아니라 성공으로 보인다."
    echo "SET LOCAL search_path = public;"
    echo ""
    echo "-- 선행 조건을 주석이 아니라 assert 로 건다. 아래 CREATE TABLE 이 IF NOT EXISTS 라"
    echo "-- 이미 이력이 있는 DB 에서도 통과해 버리기 때문이다."
    echo "DO \$\$"
    echo "BEGIN"
    echo "    IF EXISTS (SELECT 1 FROM pg_tables"
    echo "                WHERE schemaname = 'public' AND tablename = 'flyway_schema_history') THEN"
    echo "        RAISE EXCEPTION 'flyway_schema_history 가 이미 있습니다. 부트스트랩 대상이 아닙니다.';"
    echo "    END IF;"
    echo "END \$\$;"
    echo ""
    echo "CREATE TABLE IF NOT EXISTS flyway_schema_history ("
    echo "    installed_rank INTEGER NOT NULL,"
    echo "    version VARCHAR(50),"
    echo "    description VARCHAR(200) NOT NULL,"
    echo "    type VARCHAR(20) NOT NULL,"
    echo "    script VARCHAR(1000) NOT NULL,"
    echo "    checksum INTEGER,"
    echo "    installed_by VARCHAR(100) NOT NULL,"
    echo "    installed_on TIMESTAMP NOT NULL DEFAULT now(),"
    echo "    execution_time INTEGER NOT NULL,"
    echo "    success BOOLEAN NOT NULL,"
    echo "    CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank)"
    echo ");"
    echo "CREATE INDEX IF NOT EXISTS flyway_schema_history_s_idx ON flyway_schema_history (success);"
    echo ""
    echo "-- 이력 테이블을 만든 role 이 소유자가 된다. 이 SQL 을 실행하는 창구는 Supabase SQL"
    echo "-- Editor 이고 그것은 postgres 세션이므로, 이 줄이 없으면 앱이 접속하는 role($prod_role)이"
    echo "-- 이력 테이블에 아무 권한도 갖지 못하고 다음 기동이"
    echo "-- permission denied for table flyway_schema_history 로 죽는다. 심기 시점에는 신호가 없다."
    echo "--"
    echo "-- 소유권을 넘기지 않고 DML 만 준다. Flyway 는 이력 테이블에 DML 만 하고(잠금은 세션"
    echo "-- advisory lock 을 쓴다) 소유자 권한을 요구하지 않는 것을 리허설로 확인했다."
    echo "-- 소유권 이전이었다면 postgres 가 $prod_role 로 SET ROLE 할 수 있어야 하는데, 그 능력은"
    echo "-- 소유권 일괄 이전 직후 다시 꺼 두는 것이 운영 기본값이다."
    echo "--"
    echo "-- 주의: Flyway 메이저 업그레이드가 이력 테이블 구조를 바꾸면 ALTER 가 필요해져 소유권"
    echo "-- 문제가 다시 생긴다. 그때는 업그레이드 절차가 소유권 이전을 함께 다뤄야 한다."
    echo "GRANT SELECT, INSERT, UPDATE, DELETE ON flyway_schema_history TO $prod_role;"
    echo ""
}

generate() {
    local output="${1:-}" sql_dir count seeds missing rows

    work_dir="$(mktemp -d)"
    trap cleanup EXIT INT TERM
    sql_dir="$work_dir/sql"

    count="$(collect_migrations "$sql_dir")"
    echo "마이그레이션 $count 건을 모았습니다." >&2

    seeds="$(seed_versions)"
    # 목록이 비면 version not in ('') 가 되어 전건이 실행 대상으로 잡히고 INSERT 는 0건이 된다.
    # 성공처럼 보이지만 prod 에서는 첫 파일부터 실행돼 죽는다.
    [[ -n "$seeds" ]] || { echo "심기 목록이 비어 있습니다: $seed_manifest" >&2; return 1; }
    echo "심기 목록 $(printf '%s\n' "$seeds" | grep -c .) 건을 읽었습니다." >&2

    # 목록에 있는데 실제 파일이 없으면 오타이거나 파일이 지워진 것이다. 조용히 넘기면 그 파일이
    # prod 에서 실행돼 버린다.
    missing=""
    while IFS= read -r version; do
        [[ -z "$version" ]] && continue
        find "$sql_dir" -name "V${version}__*.sql" | grep -q . || missing+="  $version"$'\n'
    done <<< "$seeds"
    if [[ -n "$missing" ]]; then
        echo "심기 목록에 있으나 마이그레이션 파일이 없습니다:" >&2
        printf '%s' "$missing" >&2
        return 1
    fi

    start_scratch_db
    run_flyway_migrate "$sql_dir"

    # 실행 대상을 사람이 눈으로 확인한다. 새 마이그레이션이 들어왔는데 심기 목록에 넣지 않았다면
    # 여기에 나타난다.
    echo "" >&2
    echo "prod 에서 실행될 마이그레이션:" >&2
    scratch_query "select version || '  ' || description from flyway_schema_history
                   where version is not null
                     and version not in ($(printf "'%s'," $seeds | sed 's/,$//'))
                   order by version" | sed 's/^/  /' >&2
    echo "" >&2

    rows="$(scratch_query "
        select 'INSERT INTO flyway_schema_history'
            || ' (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)'
            || ' VALUES (' || installed_rank
            || ', ' || quote_literal(version)
            || ', ' || quote_literal(description)
            || ', ' || quote_literal(type)
            || ', ' || quote_literal(script)
            || ', ' || checksum
            || ', current_user, now(), 0, true);'
        from flyway_schema_history
        where version in ($(printf "'%s'," $seeds | sed 's/,$//'))
        order by installed_rank")"

    # 파일을 쓰기 전에 대조한다. 뒤에 두면 실패해도 산출물이 디스크에 남아 운영자가 집어들 수 있다.
    local inserted expected
    inserted="$(printf '%s\n' "$rows" | grep -c '^INSERT INTO' || true)"
    expected="$(printf '%s\n' "$seeds" | grep -c . || true)"
    [[ "$inserted" -eq "$expected" ]] || {
        echo "심기 목록 ${expected}건인데 INSERT 는 ${inserted}행입니다." >&2
        return 1
    }
    echo "INSERT ${inserted}행 (심기 목록과 일치)" >&2

    {
        bootstrap_ddl_preamble
        printf '%s\n' "$rows"
        echo ""
        echo "COMMIT;"
    } > "${output:-/dev/stdout}"

    [[ -n "$output" ]] && echo "생성했습니다: $output" >&2
    return 0
}

# psql 은 컨테이너에서 돈다. 대상이 호스트에 published 된 포트(legacy-diff 의 server-db 는
# 127.0.0.1:15434)면 URL 의 loopback 이 그 컨테이너 자신을 가리켜 connection refused 가 난다.
# host-gateway 를 심고 **authority 의 host 자리만** 바꾼다. 사용자·포트·DB 이름·쿼리는 원문
# 그대로 둔다 — DB 이름이 `localhost` 이거나 호스트가 `notlocalhost` 인 경우까지 바뀌면 안 된다.
#
# libpq URI(`scheme://[user[:pw]@]host[:port][/db][?params]`)만 다룬다. `host=... port=...`
# 키워드 형식은 손대지 않고 그대로 넘긴다.
rewrite_loopback_host() {
    local url="$1"

    case "$url" in
        *://*) ;;
        *) printf '%s' "$url"; return 0 ;;
    esac

    local scheme rest authority tail userinfo hostport host port before_slash before_query
    scheme="${url%%://*}"
    rest="${url#*://}"

    # authority 는 첫 `/` 또는 `?` 앞까지다. 둘 중 먼저 오는 쪽을 쓴다.
    before_slash="${rest%%/*}"
    before_query="${rest%%\?*}"
    authority="$before_slash"
    [[ ${#before_query} -lt ${#authority} ]] && authority="$before_query"
    tail="${rest:${#authority}}"

    userinfo=""
    case "$authority" in
        *@*) userinfo="${authority%@*}@"; hostport="${authority##*@}" ;;
        *) hostport="$authority" ;;
    esac

    port=""
    case "$hostport" in
        \[*\]*) host="${hostport%%\]*}]"; port="${hostport#"$host"}" ;;
        *:*) host="${hostport%%:*}"; port=":${hostport#*:}" ;;
        *) host="$hostport" ;;
    esac

    case "$host" in
        localhost|127.0.0.1|'[::1]') host="host.docker.internal" ;;
    esac

    printf '%s://%s%s%s%s' "$scheme" "$userinfo" "$host" "$port" "$tail"
}

# 테스트가 가짜 러너로 갈아끼울 수 있도록 실행을 분리한다(PSQL_RUNNER).
docker_psql() {
    docker run --rm --add-host=host.docker.internal:host-gateway -e PGPASSWORD \
        pgvector/pgvector:pg16 psql "$1" -Atc "$2"
}

# 기존 Flyway 이력(dev 등)과 체크섬이 같은지 대조한다. CLI 와 앱이 같은 값을 내는지 확인하는
# 용도이며, 다르면 부트스트랩 후 prod 가 checksum mismatch 로 기동에 실패한다.
#
# 대상 DB 의 flyway_schema_history 를 psql 로 직접 읽는다. `flyway info -outputType=json` 은
# 12.4.0 기준으로 checksum 필드를 내보내지 않아(실측 확인) 쓸 수 없다.
#
# 접속 정보는 libpq URL 로 받고 비밀번호는 PGPASSWORD 환경변수로 받는다. 위치 인자는 ps 출력과
# 셸 히스토리에 남는다.
#
#   PGPASSWORD=... prod-flyway-bootstrap.sh --verify 'postgres://user@host:5432/db?sslmode=require'
verify() {
    local url="$1" sql_dir count

    [[ -n "${PGPASSWORD:-}" ]] || {
        echo "PGPASSWORD 환경변수가 필요합니다." >&2
        return 2
    }

    work_dir="$(mktemp -d)"
    trap cleanup EXIT INT TERM
    sql_dir="$work_dir/sql"
    count="$(collect_migrations "$sql_dir")"
    echo "마이그레이션 $count 건을 모았습니다." >&2

    start_scratch_db
    run_flyway_migrate "$sql_dir"
    scratch_query "select version || '|' || checksum from flyway_schema_history
                   where version is not null and checksum is not null" \
        | LC_ALL=C sort -t'|' -k1,1 > "$work_dir/cli.txt"

    local psql_url psql_status=0
    psql_url="$(rewrite_loopback_host "$url")"

    # set -e 와 pipefail 때문에 psql 실패가 여기서 즉사하면 아래 안내에 도달하지 못한다.
    # 대신 종료 코드를 받아 두고 직접 판정한다. psql 이 일부 행을 낸 뒤 끊겨도 부분 결과를
    # 정상 조회로 다루지 않는다.
    ${PSQL_RUNNER:-docker_psql} "$psql_url" \
        "select version || '|' || checksum from flyway_schema_history
         where version is not null and checksum is not null" \
        > "$work_dir/target.raw" 2>"$work_dir/target.err" || psql_status=$?

    if [[ "$psql_status" -ne 0 ]]; then
        echo "대상 DB 조회가 실패했습니다(psql 종료 코드 $psql_status). 부분 결과는 쓰지 않습니다." >&2
        [[ -s "$work_dir/target.err" ]] && sed 's/^/  /' "$work_dir/target.err" >&2
        return 1
    fi

    LC_ALL=C sort -t'|' -k1,1 "$work_dir/target.raw" > "$work_dir/target.txt" || {
        echo "대상 DB 조회 결과를 정렬하지 못했습니다." >&2
        return 1
    }

    local target_rows
    target_rows="$(grep -c . "$work_dir/target.txt" || true)"
    [[ "$target_rows" -gt 0 ]] || {
        echo "대상 DB 에서 flyway_schema_history 를 읽지 못했습니다." >&2
        [[ -s "$work_dir/target.err" ]] && sed 's/^/  /' "$work_dir/target.err" >&2
        return 1
    }

    local mismatch only_cli only_target unverified_seeds failed=0
    mismatch="$(join -t'|' "$work_dir/cli.txt" "$work_dir/target.txt" \
        | awk -F'|' '$2 != $3 { print "  " $1 " cli=" $2 " target=" $3 }')"

    echo "공통 $(join -t'|' "$work_dir/cli.txt" "$work_dir/target.txt" | wc -l | tr -d ' ')건 대조" >&2

    if [[ -n "$mismatch" ]]; then
        failed=1
        echo "체크섬이 다릅니다:" >&2
        printf '%s\n' "$mismatch" >&2
    fi

    # join 은 한쪽에만 있는 줄을 조용히 버린다. 대조되지 못한 항목을 드러내지 않으면 "공통 N건
    # 일치"가 실제로 몇 건을 검증했는지 알 수 없다.
    only_cli="$(join -t'|' -v1 "$work_dir/cli.txt" "$work_dir/target.txt" | cut -d'|' -f1)"
    only_target="$(join -t'|' -v2 "$work_dir/cli.txt" "$work_dir/target.txt" | cut -d'|' -f1)"
    [[ -n "$only_cli" ]] && { echo "대상 DB 에 없어 검증되지 않음:" >&2; printf '%s\n' "$only_cli" | sed 's/^/  /' >&2; }
    [[ -n "$only_target" ]] && { echo "이 리포에 없는데 대상 DB 에는 있음:" >&2; printf '%s\n' "$only_target" | sed 's/^/  /' >&2; }

    # 심기 목록에 든 항목이 검증되지 않았다면 그 체크섬이 prod 에 그대로 들어간다. 실패로 다룬다.
    unverified_seeds="$(comm -12 <(printf '%s\n' "$only_cli" | LC_ALL=C sort) \
                                 <(seed_versions | LC_ALL=C sort))"
    if [[ -n "$unverified_seeds" ]]; then
        failed=1
        echo "심기 목록에 있으나 검증되지 않은 항목입니다. 이 체크섬이 prod 로 들어갑니다:" >&2
        printf '%s\n' "$unverified_seeds" | sed 's/^/  /' >&2
        echo "  대상 DB 를 최신 마이그레이션까지 올린 뒤 다시 실행하세요." >&2
    fi

    [[ "$failed" -eq 0 ]] && echo "체크섬 전부 일치, 심기 목록 전건 검증됨" >&2
    return "$failed"
}

main() {
    case "${1:-}" in
        --generate) shift; generate "${1:-}" ;;
        --verify)
            shift
            [[ $# -eq 1 ]] || { echo "사용법: PGPASSWORD=... $(basename "$0") --verify <libpq URL>" >&2; exit 2; }
            verify "$1"
            ;;
        *)
            echo "사용법: $(basename "$0") [--generate [출력파일] | --verify <libpq URL>]" >&2
            exit 2
            ;;
    esac
}

# 테스트가 함수만 가져다 쓸 수 있도록 직접 실행일 때만 돈다.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
