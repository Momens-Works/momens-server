#!/usr/bin/env bash
#
# prod 쌍둥이 구축 (MOM-0909).
#
# 지금까지의 리허설은 pg16 · superuser · 빈 DB · 무트래픽에서 돌았다. 그 넷이 동시에 prod 와
# 달라 **구조적으로 재현할 수 없던 축**이 남았다(설계 문서 8절). 이 스크립트는 그 축을 닫기
# 위해 prod 와 같은 형상의 DB 를 로컬에 세운다.
#
#   PostgreSQL 17          prod 는 Supabase 17.6 이다. 하네스는 pg16 이었다
#   레거시 000001~000019   레거시 러너와 같은 방식(파일별 트랜잭션 + schema_migrations 행)
#   Supabase role 형상     anon / authenticated / service_role + ALTER DEFAULT PRIVILEGES
#   momens_server          비-superuser · 무소유 · CREATE on public · 레거시 테이블 DML
#   합성 데이터            tasks 10 만 행
#
# 이 쌍둥이는 direct 접속을 전제한다. prod DB 포트가 5432 로 확인돼(2026-08-26) 세션이 유지되므로
# 그 전제가 맞다. 접속 정보 교체 시 트랜잭션 pooler(:6543) 주소로 바뀌면 세션 단위 잠금과
# init-sqls 가 성립하지 않아 여기서 얻은 락 결과가 무효가 된다.
#
# 결과물은 `twin_base` 데이터베이스다. rehearse.sh 가 시나리오마다 TEMPLATE 으로 복제해
# 매번 같은 출발점에서 시작한다.
#
# 사용법: scripts/prod-twin/build.sh

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$here/../.." && pwd)"
legacy_migrations="$repo_root/../momens-api/migrations"

container="momens-prod-twin"
image="pgvector/pgvector:pg17"
port=15499
base_db="twin_base"

psql_root() { docker exec -i "$container" psql -U postgres -d "${2:-postgres}" -q -v ON_ERROR_STOP=1 -Atc "$1"; }

[[ -d "$legacy_migrations" ]] || {
    echo "레거시 마이그레이션을 찾지 못했습니다: $legacy_migrations" >&2
    echo "momens-api 를 momens-server 와 같은 부모 디렉터리에 두어야 합니다." >&2
    exit 1
}

echo "== 1/6 PostgreSQL 17 컨테이너 =="
docker rm -f "$container" >/dev/null 2>&1 || true
docker run -d --name "$container" \
    -e POSTGRES_DB=postgres -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
    -p "127.0.0.1:$port:5432" "$image" >/dev/null
for _ in $(seq 1 60); do
    docker exec "$container" pg_isready -U postgres -d postgres >/dev/null 2>&1 && break
    sleep 1
done
echo "   $(psql_root 'select version()' | cut -d, -f1)"

psql_root "CREATE DATABASE $base_db"

echo "== 2/6 레거시 마이그레이션 =="
# 레거시 러너(internal/platform/db/migrations.go)를 그대로 흉내낸다. 파일 하나가 자기
# 트랜잭션 안에서 돌고 schema_migrations 행과 함께 커밋된다. version 은 확장자를 뗀 파일명이다.
psql_root "CREATE TABLE IF NOT EXISTS schema_migrations (
               version TEXT PRIMARY KEY,
               applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW())" "$base_db"
count=0
for file in "$legacy_migrations"/*.sql; do
    version="$(basename "$file" .sql)"
    {
        echo "BEGIN;"
        cat "$file"
        echo
        echo "INSERT INTO schema_migrations (version) VALUES ('$version');"
        echo "COMMIT;"
    } | docker exec -i "$container" psql -U postgres -d "$base_db" -q -v ON_ERROR_STOP=1 >/dev/null \
        || { echo "레거시 마이그레이션 실패: $version" >&2; exit 1; }
    count=$((count + 1))
done
echo "   $count 건 적용, 테이블 $(psql_root "select count(*) from pg_tables where schemaname='public'" "$base_db") 개"

# prod 형상의 정의다. 여기서 flyway_schema_history 가 있으면 쌍둥이가 아니다.
[[ "$(psql_root "select count(*) from pg_tables where schemaname='public' and tablename='flyway_schema_history'" "$base_db")" == "0" ]] \
    || { echo "flyway_schema_history 가 이미 있습니다. prod 형상이 아닙니다." >&2; exit 1; }

echo "== 3/6 role =="
docker exec -i "$container" psql -U postgres -d "$base_db" -q -v ON_ERROR_STOP=1 < "$here/roles.sql"
echo "   anon / authenticated / service_role / momens_server"

# roles.sql 의 GRANT 대상과 "서버 엔티티가 매핑하면서 레거시가 이미 만든 테이블"이 어긋나면
# prod 에서 런타임에 permission denied 가 난다. 부트스트랩과 무관한 시점에 터지므로 여기서 센다.
mapped="$(grep -rhoE '@Table\(\s*name\s*=\s*"[a-z_]+"' --include='*.java' "$repo_root/modules" "$repo_root/app" 2>/dev/null \
    | grep -oE '"[a-z_]+"' | tr -d '"' | sort -u)"
existing="$(psql_root "select tablename from pg_tables where schemaname='public'" "$base_db" | sort)"
overlap="$(comm -12 <(printf '%s\n' "$mapped") <(printf '%s\n' "$existing") | grep -c .)"
granted="$(psql_root "select count(distinct table_name) from information_schema.table_privileges
                      where grantee='momens_server' and privilege_type='SELECT'" "$base_db")"
# prod 는 `tasks` 를 의도적으로 빼고 18 개를 GRANT 했다(소유권 이전으로 대체하려던 판단).
# 쌍둥이도 그 형상을 그대로 재현하므로 한 개가 비는 것이 정상이다.
echo "   레거시 테이블 중 엔티티가 매핑하는 것 $overlap 개 / DML GRANT $granted 개 (tasks 제외)"
# 어긋난 채로 twin_base 를 만들면 이후 리허설이 prod 와 다른 role 형상에서 돌고, 그 결과를
# 그대로 믿게 된다. 이 쌍둥이가 존재하는 이유가 정확히 그 실패(superuser 리허설의 거짓 통과)라
# 경고로 넘기지 않는다.
[[ "$overlap" -eq $((granted + 1)) ]] || {
    echo "GRANT 대상 차이가 tasks 한 개가 아닙니다. roles.sql 의 GRANT 목록을 확인하세요." >&2
    exit 1
}

echo "== 4/6 Supabase 고유 형상 =="
# 확장 스키마 분리와 event trigger. 레거시 마이그레이션만으로는 재현되지 않는다.
docker exec -i "$container" psql -U postgres -d "$base_db" -q -v ON_ERROR_STOP=1 < "$here/supabase-shape.sql"
echo "   uuid-ossp → $(psql_root "select n.nspname from pg_extension e join pg_namespace n on n.oid = e.extnamespace where e.extname = 'uuid-ossp'" "$base_db") · event trigger $(psql_root 'select count(*) from pg_event_trigger' "$base_db") 개"

echo "== 5/6 합성 데이터 =="
docker exec -i "$container" psql -U postgres -d "$base_db" -q -v ON_ERROR_STOP=1 < "$here/data.sql"
echo "   tasks $(psql_root 'select count(*) from tasks' "$base_db") 행"

echo "== 6/6 완료 =="
psql_root "ALTER DATABASE $base_db WITH IS_TEMPLATE true" >/dev/null
cat <<MSG

   컨테이너  $container  (127.0.0.1:$port)
   템플릿    $base_db
   시나리오  scripts/prod-twin/rehearse.sh

MSG
