#!/usr/bin/env bash
#
# prod 쌍둥이 리허설 시나리오 (MOM-0909).
#
# build.sh 가 만든 twin_base 를 시나리오마다 TEMPLATE 으로 복제해, 매번 같은 출발점
# (레거시 스키마 + 데이터 + Flyway 이력 없음)에서 시작한다.
#
# 앱은 릴리스와 같은 방식으로 띄운다 — prod 프로필 bootJar 에 k8s ConfigMap 이 넣을 환경변수를
# 그대로 준다. Flyway CLI 로 대신하지 않는다. CLI 는 명령행 인자를 쓰므로 ConfigMap →
# JAVA_TOOL_OPTIONS → JVM 프로퍼티 → Spring 바인딩 경로를 검증하지 못한다.
#
# 사용법:
#   scripts/prod-twin/rehearse.sh              전 시나리오
#   scripts/prod-twin/rehearse.sh baseline     하나만
#
# 시나리오:
#   baseline       정상 경로. 소유권 이전 → 심기 28행 → 부트스트랩 → validate 기동 → 재기동
#   no-ownership   tasks 소유권 이전 생략
#   no-references  users REFERENCES 권한 누락
#   no-set-option  창구가 momens_server 로 SET ROLE 할 수 없음
#   no-search-path 확장 스키마가 search_path 에 없음
#   bulk-ownership 레거시 테이블 20개를 한 번에 넘길 때
#   ownership-reverted  부트스트랩 성공 후 tasks 소유권을 되돌릴 때
#   history-owner  이력 테이블을 postgres 가 만든 경우
#   lock           레거시가 tasks 를 ACCESS EXCLUSIVE 로 잡고 있는 경우
#   checksum       심은 체크섬 하나가 파일과 다른 경우

set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$here/../.." && pwd)"

container="momens-prod-twin"
base_db="twin_base"
db="twin"
port=15499
app_port=18099
jar="$repo_root/app/build/libs/app-0.1.0-SNAPSHOT.jar"
work="$(mktemp -d)"
bootstrap_sql="$work/bootstrap.sql"

trap 'stop_app; rm -rf "$work"' EXIT INT TERM

pass=0; fail=0

# --- 도구 -------------------------------------------------------------------

# root  = 컨테이너 superuser. 관찰과 "관리자 조치" 대역이다.
# op    = sb_postgres. Supabase SQL Editor 대역이며 **비-superuser** 다. 심기 SQL 은 이쪽으로
#         실행한다 — superuser 로 심으면 ALTER TABLE ... OWNER TO 의 SET ROLE 검사가 통째로
#         건너뛰어져 prod 에서만 터지는 실패를 놓친다.
root() { docker exec -i "$container" psql -U postgres -d "${2:-$db}" -q -v ON_ERROR_STOP=1 -Atc "$1"; }
op()   { docker exec -i -e PGPASSWORD=sb_postgres "$container" \
             psql -U sb_postgres -h 127.0.0.1 -d "${2:-$db}" -v ON_ERROR_STOP=1 -Atc "$1" 2>&1; }
op_file() { docker exec -i -e PGPASSWORD=sb_postgres "$container" \
                psql -U sb_postgres -h 127.0.0.1 -d "${2:-$db}" -v ON_ERROR_STOP=1 < "$1" 2>&1; }
server() { docker exec -i -e PGPASSWORD=momens_server "$container" \
             psql -U momens_server -h 127.0.0.1 -d "${2:-$db}" -v ON_ERROR_STOP=1 -Atc "$1" 2>&1; }

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
step() { printf '   %s\n' "$*"; }
ok()   { pass=$((pass + 1)); printf '   \033[32mOK\033[0m    %s\n' "$*"; }
bad()  { fail=$((fail + 1)); printf '   \033[31mFAIL\033[0m  %s\n' "$*"; }

# 기대한 문구가 나왔는지로 판정한다. 시나리오의 절반이 "실패해야 정상"이라 종료코드만으로는
# 의도한 이유로 실패했는지를 가릴 수 없다.
expect() {
    local label="$1" needle="$2" haystack="$3"
    if grep -qF "$needle" <<<"$haystack"; then ok "$label"
    else bad "$label — '$needle' 를 찾지 못했습니다"; printf '%s\n' "$haystack" | tail -20 | sed 's/^/         /'; fi
}

reset_db() {
    stop_app
    # role 멤버십은 데이터베이스가 아니라 클러스터에 딸려 있어 DROP DATABASE 로 사라지지 않는다.
    # 회수하지 않으면 앞 시나리오가 준 SET 능력이 뒤 시나리오까지 따라가 no-set-option 이
    # 통과해 버린다.
    docker exec -i "$container" psql -U postgres -d postgres -q -Atc \
        "REVOKE momens_server FROM sb_postgres" >/dev/null 2>&1
    docker exec -i "$container" psql -U postgres -d postgres -q -Atc \
        "ALTER ROLE momens_server RESET search_path" >/dev/null 2>&1
    docker exec -i "$container" psql -U postgres -d postgres -q -Atc \
        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$db'" >/dev/null 2>&1
    docker exec -i "$container" psql -U postgres -d postgres -q -v ON_ERROR_STOP=1 -Atc \
        "DROP DATABASE IF EXISTS $db" >/dev/null
    docker exec -i "$container" psql -U postgres -d postgres -q -v ON_ERROR_STOP=1 -Atc \
        "CREATE DATABASE $db TEMPLATE $base_db" >/dev/null
}

# 부트스트랩의 DDL 선행 조건은 셋이다. 지금까지의 계획에는 첫째만 있었다 — 나머지 둘은 이
# 쌍둥이가 찾았다.
# 선행 조건은 셋이고 각각 독립이다. 시나리오가 하나씩만 빼서 그 하나의 증상을 보도록 따로 둔다.
prereq_tasks_ownership() {
    # prod 의 GRANT 18 개는 tasks 를 의도적으로 뺐다 — 소유권을 넘기면 소유자로서 DML 이 따라오기
    # 때문이다. 그래서 tasks 의 런타임 DML 이 소유권 이전 성공에만 매달린다. 이전이 무산되면
    # 부트스트랩과 무관하게 서비스가 tasks 를 못 읽는다. 소유자가 부여하는 지금 끊어 둔다.
    #
    # **소유권 이전보다 먼저 해야 한다.** 이전 뒤에는 창구가 소유자가 아니라 GRANT OPTION 이
    # 없다. 그리고 이 GRANT 는 이전을 **견디지 못한다** — ALTER TABLE ... OWNER TO 는 현재
    # 소유자를 grantee 로 하는 ACL 항목을 새 소유자로 옮기므로, momens_server 가 소유자가 되는
    # 순간 이 항목은 소유자 항목에 흡수되고 나중에 소유권을 되돌리면 함께 떠난다
    # (ownership-reverted 시나리오). 되돌릴 때 다시 부여하는 것이 롤백 절차의 몫이다.
    op "GRANT SELECT, INSERT, UPDATE, DELETE ON tasks TO momens_server" >/dev/null

    # 실행 집합이 tasks 를 ALTER 한다. ALTER TABLE 은 GRANT 대상이 아니라 소유자 권한이라
    # 소유권 이전 말고 다른 길이 없다. 소유자가 바뀌면 레거시(창구 role)의 DML 이 사라지므로
    # 되돌려준다.
    #
    # **창구 role 로 실행한다.** 관리자가 쓰는 것이 SQL Editor = 비-superuser 세션이기 때문이다.
    # 그래서 이 명령은 prereq_operator_set_role 에 의존한다 — 비-superuser 는 대상 role 로
    # SET ROLE 할 수 있어야 소유권을 넘길 수 있다. superuser 로 돌리면 그 의존이 감춰진다.
    op "ALTER TABLE tasks OWNER TO momens_server" >/dev/null
    op "GRANT SELECT, INSERT, UPDATE, DELETE ON tasks TO sb_postgres" >/dev/null
}

prereq_users_references() {
    # V20260810090000 이 user_identities 를 만들며 users(id) 를 참조한다. FK 를 거는 쪽은
    # 참조당하는 테이블에 REFERENCES 가 필요하고, 이것은 DML 권한에 포함되지 않는다.
    # 소유권까지는 필요 없다.
    root "GRANT REFERENCES ON users TO momens_server" >/dev/null
}

prereq_operator_set_role() {
    # 심기 SQL 이 이력 테이블 소유권을 momens_server 로 넘긴다. 비-superuser 창구가 그러려면
    # 그 role 로 SET ROLE 할 수 있어야 한다. CREATEROLE 의 자동 멤버십은 set=false 라 안 된다.
    root "GRANT momens_server TO sb_postgres WITH SET TRUE" >/dev/null
}

prereq_extensions_search_path() {
    # 실행 집합 2건(V20260810090000, V20260823110000)이 스키마 한정 없이 uuid_generate_v4() 를
    # 쓴다. Supabase 는 uuid-ossp 를 extensions 스키마에 두는데 momens_server 의 search_path 에는
    # 그것이 없다(prod 실측: pg_db_role_setting 에 항목 없음 → 서버 기본값 "$user", public).
    #
    # **둘 다 필요하다.** USAGE 가 없으면 search_path 에 넣어도 이름 해석에서 스키마가 보이지
    # 않고, search_path 가 없으면 USAGE 가 있어도 한정 없는 호출이 해석되지 않는다. 쌍둥이에서
    # (a) search_path 만 → 실패, (b) 둘 다 → 성공, (c) USAGE 만 → 실패로 확인했다.
    #
    # 마이그레이션 파일을 extensions.uuid_generate_v4() 로 고치는 길은 없다 — 두 파일 모두
    # local·dev 이력에 체크섬이 박혀 있어 고치면 그 환경들이 checksum mismatch 로 죽는다.
    root "GRANT USAGE ON SCHEMA extensions TO momens_server" >/dev/null
    root 'ALTER ROLE momens_server SET search_path = "$user", public, extensions' >/dev/null
}

# **순서가 있다.** prereq_tasks_ownership 이 prereq_operator_set_role 에 의존한다.
grant_ddl_prerequisites() {
    prereq_operator_set_role
    prereq_tasks_ownership
    prereq_users_references
    prereq_extensions_search_path
}

app_log=""
app_pid=""

# 릴리스와 같은 형태로 띄운다. JAVA_TOOL_OPTIONS 의 값 전체를 큰따옴표로 감싸는 것이 요점이다 —
# 없으면 JVM 이 공백에서 인자를 쪼개 Unrecognized option 으로 기동 자체가 실패한다.
start_app() {
    local extra_java="${1:-}" user="${2:-momens_server}"
    app_log="$work/app-$RANDOM.log"
    SPRING_PROFILES_ACTIVE=prod \
    SPRING_FLYWAY_ENABLED=true \
    DATABASE_URL="jdbc:postgresql://127.0.0.1:$port/$db" \
    DATABASE_USERNAME="$user" DATABASE_PASSWORD="$user" \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 $extra_java" \
    MOMENS_AUTH_JWT_SECRET="$(printf '0%.0s' {1..64})" \
    MOMENS_AUTH_GOOGLE_CLIENT_ID=dummy MOMENS_AUTH_GOOGLE_CLIENT_SECRET=dummy \
    MOMENS_AUTH_GOOGLE_AUDIENCES=dummy \
    MOMENS_AUTH_GOOGLE_REDIRECT_URI='http://localhost:8080/api/auth/google/callback' \
    MOMENS_AUTH_WEB_SUCCESS_REDIRECT_URI='http://localhost/' \
    MOMENS_AUTH_WEB_FAILURE_REDIRECT_URI='http://localhost/login' \
    CORS_ALLOWED_ORIGINS='http://localhost' SERVER_PORT="$app_port" \
        java -jar "$jar" > "$app_log" 2>&1 &
    app_pid=$!
}

stop_app() {
    [[ -n "${app_pid:-}" ]] || return 0
    kill "$app_pid" >/dev/null 2>&1
    wait "$app_pid" >/dev/null 2>&1
    app_pid=""
}

# 기동 성공/실패가 확정될 때까지 기다린다. 성공은 Started 로그, 실패는 프로세스 종료다.
await_app() {
    local timeout="${1:-180}" i
    for i in $(seq 1 "$timeout"); do
        grep -q "Started MomensServerApplication" "$app_log" 2>/dev/null && { echo started; return 0; }
        kill -0 "$app_pid" 2>/dev/null || { echo exited; return 1; }
        sleep 1
    done
    echo timeout; return 1
}

# 레거시 트래픽 대역. tasks 에 짧은 UPDATE 를 200ms 간격으로 계속 던지고 각 문장의 소요를 잰다.
# 부트스트랩이 tasks 를 ACCESS EXCLUSIVE 로 잡는 동안 이 UPDATE 들이 막히므로, **가장 오래 막힌
# 한 문장**이 곧 레거시가 겪는 최악의 지연이고 창구에서 잡아야 할 예산이다.
#
# pg_locks 폴링으로 재지 않는다. docker exec 왕복이 폴링 간격보다 커서 표본이 한두 개밖에 잡히지
# 않고, 그러면 구간 길이를 말할 수 없다. 막히는 쪽에서 재는 것이 직접적이다.
#
# 접속 role 은 창구 role 이다. 소유권을 넘긴 뒤에도 DML 을 되돌려줬으므로 레거시와 같은 위치다.
legacy_writer_start() {
    legacy_writer_out="$work/writer-$RANDOM.txt"
    # \watch 는 백그라운드에서 반복되지 않는다. 문장을 명시적으로 늘어놓는다.
    # UPDATE 와 짧은 sleep 을 번갈아 둔다 — sleep 없이 붙이면 RowExclusiveLock 이 끊이지 않아
    # 부트스트랩이 ACCESS EXCLUSIVE 를 아예 못 잡고 lock_timeout 으로 죽는다. 그건 별개 시나리오다.
    {
        echo '\timing on'
        for _ in $(seq 1 400); do
            echo "UPDATE tasks SET updated_at = now() WHERE id = '44444444-4444-4444-4444-000000000001';"
            echo "SELECT pg_sleep(0.05);"
        done
    } > "$work/writer.sql"
    docker exec -i -e PGPASSWORD=sb_postgres "$container" \
        psql -U sb_postgres -h 127.0.0.1 -d "$db" -q < "$work/writer.sql" \
        > "$legacy_writer_out" 2>&1 &
    legacy_writer_pid=$!
}

legacy_writer_report() {
    kill "$legacy_writer_pid" >/dev/null 2>&1; wait "$legacy_writer_pid" >/dev/null 2>&1
    python3 - "$legacy_writer_out" <<'PYEOF'
import re, sys
# UPDATE 와 pg_sleep 이 번갈아 나오므로 짝수 번째(UPDATE)만 본다.
times = [float(m) for m in re.findall(r"^Time: ([0-9.]+) ms", open(sys.argv[1]).read(), re.M)][::2]
if not times:
    print("   레거시 writer                       표본 없음")
else:
    blocked = [t for t in times if t > 100]
    print("   레거시 writer UPDATE %d 문장          최장 %.0f ms, 100ms 초과 %d 문장"
          % (len(times), max(times), len(blocked)))
    if blocked:
        print("   레거시가 막힌 총 시간               %.1f초" % (sum(blocked) / 1000))
PYEOF
}

# 앱 로그(구조화 JSON)의 @timestamp 로 구간을 나눈다.
timeline() {
    local wall_start="$1" wall_end="$2"
    python3 - "$app_log" "$wall_start" "$wall_end" <<'PYEOF'
import json, sys
from datetime import datetime

log, wall_start, wall_end = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
first = connected = failed = None
timeouts = 0

for line in open(log, encoding="utf-8", errors="replace"):
    line = line.strip()
    if not line.startswith("{"):
        continue
    try:
        rec = json.loads(line)
    except ValueError:
        continue
    ts = datetime.fromisoformat(rec["@timestamp"])
    msg = rec.get("message", "")
    if first is None:
        first = ts
    # Flyway 가 대상 DB 를 잡은 시점. 이 줄 뒤부터가 마이그레이션 구간이다.
    if connected is None and msg.startswith("Database: jdbc:postgresql"):
        connected = ts
    if "canceling statement due to lock timeout" in msg:
        timeouts += msg.count("canceling statement due to lock timeout")
        if failed is None:
            failed = ts

def secs(a, b):
    return "%.1f" % (b - a).total_seconds() if a and b else "?"

print("   구간 분해")
print("     첫 로그 → Flyway 가 DB 를 잡음   %s초  (JVM + Spring 컨텍스트)" % secs(first, connected))
print("     DB 를 잡음 → 포기                %s초  ← 절차에 적을 값" % secs(connected, failed))
print("     프로세스 전체 (wall-clock)       %d초" % (wall_end - wall_start))
print("     lock_timeout 소진 횟수           %d" % timeouts)
PYEOF
}

toggles='-Dspring.flyway.out-of-order=true -Dspring.flyway.group=true'
lock_opt='-Dspring.flyway.init-sqls="SET lock_timeout TO '"'"'5s'"'"'"'

# --- 준비 -------------------------------------------------------------------

[[ -f "$jar" ]] || { echo "bootJar 가 없습니다. ./gradlew :app:bootJar" >&2; exit 1; }
docker inspect "$container" >/dev/null 2>&1 || { echo "쌍둥이가 없습니다. scripts/prod-twin/build.sh" >&2; exit 1; }

say "부트스트랩 INSERT 생성"
"$repo_root/scripts/prod-flyway-bootstrap.sh" --generate "$bootstrap_sql" 2>&1 | grep -E '심기 목록|INSERT ' | sed 's/^/   /'

# --- 시나리오 ---------------------------------------------------------------

scenario_baseline() {
    say "baseline — 정상 경로"
    reset_db
    grant_ddl_prerequisites
    step "tasks 소유자: $(root "select tableowner from pg_tables where tablename='tasks'")"

    # 실행 창구는 Supabase SQL Editor = postgres 세션이다. momens_server 로 psql 을 여는 선택지는
    # 없다 — 그 비밀번호는 생성 후 GitHub Secret 에만 들어갔고 아무도 모른다. 생성물이 트랜잭션
    # 안에서 이력 테이블 소유권을 넘기므로 postgres 로 심어도 성립한다(history-owner 시나리오).
    local out
    out="$(op_file "$bootstrap_sql")"
    expect "심기 28행 INSERT (SQL Editor 대역 = 비-superuser)" "COMMIT" "$out"
    step "flyway_schema_history 소유자: $(root "select tableowner from pg_tables where tablename='flyway_schema_history'")"
    step "이력 $(server "select count(*) from flyway_schema_history") 행, 최고 version $(server "select max(version) from flyway_schema_history")"

    legacy_writer_start
    start_app "$toggles $lock_opt"
    local result; result="$(await_app 180)"
    if [[ "$result" == started ]]; then ok "부트스트랩 + ddl-auto=validate 기동"
    else bad "기동 실패 ($result)"; grep -iE 'error|caused by|exception' "$app_log" | head -10 | sed 's/^/         /'; fi
    legacy_writer_report

    # 심은 행의 installed_rank 는 스크래치 DB 의 값을 그대로 옮긴 것이라 1~28 이 아니다.
    # 실행 건수는 rank 가 아니라 심기 목록에 없는 version 의 수로 센다.
    local seeded; seeded="$(grep -oE '^[0-9]+' "$repo_root/scripts/prod-flyway-bootstrap-seed.txt" \
        | sed "s/.*/'&'/" | paste -sd, -)"
    step "이력 총 $(server "select count(*) from flyway_schema_history") 행 = 심기 28 + 실행 $(server "select count(*) from flyway_schema_history where version not in ($seeded)") 건"
    step "최종 version: $(server "select max(version) from flyway_schema_history")"
    step "실패 행: $(server "select count(*) from flyway_schema_history where not success") 건"
    step "tasks 컬럼 role/origin_type/next_action: $(server "select count(*) from information_schema.columns where table_name='tasks' and column_name in ('role','origin_type','next_action')")/3"
    step "tasks_role_check 귀속: $(server "select coalesce((select conrelid::regclass::text from pg_constraint where conname='tasks_role_check'),'없음')")"
    step "task_roles 생성 여부: $(server "select count(*) from pg_tables where tablename='task_roles'") (0 이어야 한다)"
    step "tasks 행 수: $(server "select count(*) from tasks") (10 만이어야 한다)"
    step "새 테이블 소유자: $(server "select distinct tableowner from pg_tables where tablename in ('signals','outbox_events','user_identities')")"
    step "anon 이 signals 를 읽는가: $(server "select has_table_privilege('anon','signals','SELECT')")"

    # lock_timeout 은 틀려도 조용하다. 서버가 실제로 받았는지 로그로 확인한다.
    if grep -q "lock_timeout" "$app_log"; then step "init-sqls: 앱 로그에 보임"; fi

    say "baseline 재기동 — 토글 없이"
    stop_app
    start_app ""
    result="$(await_app 180)"
    if [[ "$result" == started ]]; then ok "out-of-order·group 없이 재기동"
    else bad "재기동 실패 ($result)"; grep -iE 'error|caused by' "$app_log" | head -10 | sed 's/^/         /'; fi
    expect "Flyway 가 추가 실행 없음" "is up to date. No migration necessary" "$(cat "$app_log")"
    stop_app
}

scenario_no_ownership() {
    say "no-ownership — 소유권 이전 생략"
    reset_db
    prereq_operator_set_role
    prereq_users_references
    step "tasks 소유자: $(root "select tableowner from pg_tables where tablename='tasks'") (이전하지 않음)"

    local out
    out="$(op_file "$bootstrap_sql")"
    expect "심기 자체는 성공한다" "COMMIT" "$out"

    start_app "$toggles $lock_opt"
    local result; result="$(await_app 180)"
    if [[ "$result" == started ]]; then bad "기동에 성공했습니다. 소유권 없이 통과하면 안 됩니다"
    else ok "기동 실패 ($result)"; fi
    expect "must be owner of table tasks" "must be owner of table tasks" "$(cat "$app_log")"
    step "실패 후 스키마: 새 테이블 $(server "select count(*) from pg_tables where tablename in ('signals','outbox_events','user_identities')") 개 (group=true 면 0)"
    step "이력 실패 행: $(server "select count(*) from flyway_schema_history where not success") 건"
    stop_app
}

scenario_no_references() {
    say "no-references — tasks 소유권만 넘기고 users REFERENCES 를 빠뜨린 경우"
    step "지금까지의 계획이 정확히 이 상태였다"
    reset_db
    prereq_operator_set_role
    prereq_tasks_ownership
    # V20260810090000 은 uuid_generate_v4() 도 쓴다. search_path 선행 조건이 없으면 그쪽에 먼저
    # 걸려 이 시나리오가 보려는 증상이 아니게 된다. 두 실패가 같은 파일에 있다는 뜻이기도 하다 —
    # prod 에서 search_path 를 고치면 그 다음에 REFERENCES 가 드러난다.
    prereq_extensions_search_path
    step "users REFERENCES: $(root "select has_table_privilege('momens_server','users','REFERENCES')")"

    # 심기가 조용히 실패하면 앱이 42건을 처음부터 실행해 엉뚱한 이유로 죽는다. 그러면 이
    # 시나리오가 보려는 증상이 아니게 되므로 심기 성공을 먼저 확인한다.
    expect "심기 성공" "COMMIT" "$(op_file "$bootstrap_sql")"

    start_app "$toggles $lock_opt"
    local result; result="$(await_app 180)"
    if [[ "$result" == started ]]; then bad "기동에 성공했습니다. REFERENCES 없이 통과하면 안 됩니다"
    else ok "기동 실패 ($result)"; fi
    expect "permission denied for table users" "permission denied for table users" "$(cat "$app_log")"
    step "죽은 위치: $(grep -oE 'V20260810090000__[a-z_]+\.sql' "$app_log" | head -1)"
    step "실패 후 새 테이블: $(server "select count(*) from pg_tables where tablename in ('signals','outbox_events','user_identities')") 개 (group=true 면 0)"
    stop_app
}

scenario_no_set_option() {
    say "no-set-option — 창구가 momens_server 로 SET ROLE 할 수 없는 경우"
    step "심기 SQL 이 이력 테이블 소유권을 넘기는데, 비-superuser 는 대상 role 로 SET ROLE 할 수"
    step "있어야 그게 된다. CREATEROLE 의 자동 멤버십은 set=false 라 쓸 수 없다."
    reset_db
    prereq_users_references
    step "창구가 momens_server 로 SET ROLE 가능: $(root "select pg_has_role('sb_postgres','momens_server','USAGE')")"

    # 이 능력이 없으면 **선행 조건의 소유권 이전부터** 막힌다. 심기까지 가지도 못한다.
    expect "tasks 소유권 이전이 먼저 막힌다" "must be able to SET ROLE" \
           "$(op "ALTER TABLE tasks OWNER TO momens_server")"
    step "tasks 소유자: $(root "select tableowner from pg_tables where tablename='tasks'") (그대로)"

    # 소유권을 관리자가 다른 경로로 넘겼다고 가정해도 심기가 같은 이유로 막힌다.
    root "ALTER TABLE tasks OWNER TO momens_server" >/dev/null
    local out; out="$(op_file "$bootstrap_sql")"
    if grep -qF "COMMIT" <<<"$out"; then bad "심기에 성공했습니다. SET 능력 없이 통과하면 안 됩니다"
    else ok "심기 실패"; fi
    expect "must be able to SET ROLE" "must be able to SET ROLE" "$out"

    # 단일 트랜잭션이라 앞의 INSERT 까지 통째로 롤백된다. 반쯤 심긴 상태가 남지 않는 것이 요점이다.
    step "flyway_schema_history 존재: $(root "select count(*) from pg_tables where tablename='flyway_schema_history'") (0 이어야 한다)"
}

scenario_history_owner() {
    say "history-owner — postgres 세션으로 심는 경우"
    step "실행 창구는 Supabase SQL Editor = postgres 세션이다. momens_server 로 psql 을 여는"
    step "선택지는 없다 — 그 비밀번호는 생성 후 GitHub Secret 에만 들어갔고 아무도 모른다."
    step "그래서 생성물이 트랜잭션 안에서 소유권을 넘긴다."

    say "history-owner (a) — 소유권 이전 줄이 없으면"
    reset_db
    grant_ddl_prerequisites
    grep -v '^ALTER TABLE flyway_schema_history OWNER TO' "$bootstrap_sql" > "$work/nowner.sql"
    op_file "$work/nowner.sql" >/dev/null 2>&1
    step "flyway_schema_history 소유자: $(root "select tableowner from pg_tables where tablename='flyway_schema_history'")"
    step "momens_server 의 INSERT 권한: $(root "select has_table_privilege('momens_server','flyway_schema_history','INSERT')")"

    start_app "$toggles $lock_opt"
    local result; result="$(await_app 180)"
    if [[ "$result" == started ]]; then bad "기동에 성공했습니다. 소유권 없이 통과하면 안 됩니다"
    else ok "기동 실패 ($result)"; fi
    expect "permission denied for table flyway_schema_history" \
           "permission denied for table flyway_schema_history" "$(cat "$app_log")"
    stop_app

    say "history-owner (b) — 생성물 그대로 postgres 로 심으면"
    reset_db
    grant_ddl_prerequisites
    local out
    out="$(op_file "$bootstrap_sql")"
    expect "심기 성공" "COMMIT" "$out"
    step "flyway_schema_history 소유자: $(root "select tableowner from pg_tables where tablename='flyway_schema_history'")"

    start_app "$toggles $lock_opt"
    result="$(await_app 180)"
    if [[ "$result" == started ]]; then ok "postgres 로 심어도 momens_server 가 끝까지 돈다"
    else bad "기동 실패 ($result)"; grep -ioE "permission denied for table [a-z_]+" "$app_log" | head -3 | sed 's/^/         /'; fi
    step "이력 총 $(server "select count(*) from flyway_schema_history") 행, 실패 $(server "select count(*) from flyway_schema_history where not success") 건"
    stop_app
}

scenario_bulk_ownership() {
    say "bulk-ownership — 레거시 테이블 20개를 한 번에 넘기면"
    step "ADR-0019 의 최종 상태다. ALTER TABLE 은 GRANT 로 얻을 수 없어 소유권 말고 길이 없다."
    reset_db
    prereq_operator_set_role
    prereq_extensions_search_path

    local own_sql="$work/ownership.sql"
    "$repo_root/scripts/prod-ownership-transfer.sh" --generate "$own_sql" 2>/dev/null

    # GRANT ALL 로는 ALTER 가 안 된다는 것을 먼저 못박는다. 이것이 소유권이 필요한 이유다.
    root "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO momens_server" >/dev/null
    expect "GRANT ALL 로도 ALTER 는 안 된다" "must be owner of table tasks" \
           "$(server "ALTER TABLE tasks ADD COLUMN twin_probe TEXT")"
    root "REVOKE ALL ON ALL TABLES IN SCHEMA public FROM momens_server" >/dev/null
    # prod 형상(18건 DML, tasks 제외)으로 되돌린다.
    docker exec -i "$container" psql -U postgres -d "$db" -q -v ON_ERROR_STOP=1 \
        -c "$(grep -A8 'GRANT SELECT, INSERT, UPDATE, DELETE ON' "$here/roles.sql" | tail -n +1 | head -7)" >/dev/null 2>&1

    expect "일괄 이전 성공" "COMMIT" "$(op_file "$own_sql")"
    step "momens_server 소유 테이블: $(root "select count(*) from pg_tables where schemaname='public' and tableowner='momens_server'") 개"
    step "schema_migrations 소유자: $(root "select tableowner from pg_tables where tablename='schema_migrations'") (레거시 이력, 넘기면 안 된다)"

    # 요점. 이전 소유자가 권한을 잃으면 레거시가 끊긴다.
    local orphan; orphan="$(root "select coalesce(string_agg(tablename, ' '), '(없음)')
        from pg_tables where schemaname='public' and tableowner='momens_server'
          and not has_table_privilege('sb_postgres', schemaname||'.'||tablename, 'SELECT')")"
    if [[ "$orphan" == "(없음)" ]]; then ok "레거시가 20개를 전부 읽는다"
    else bad "레거시가 못 읽는 테이블이 있습니다: $orphan"; fi

    orphan="$(root "select coalesce(string_agg(tablename, ' '), '(없음)')
        from pg_tables where schemaname='public' and tableowner='momens_server'
          and not has_table_privilege('sb_postgres', schemaname||'.'||tablename, 'UPDATE')")"
    [[ "$orphan" == "(없음)" ]] && ok "레거시가 20개를 전부 쓴다" || bad "레거시가 못 쓰는 테이블: $orphan"

    step "anon 의 tasks SELECT: $(root "select has_table_privilege('anon','tasks','SELECT')") (영향 없어야 한다)"
    expect "momens_server 가 이제 ALTER 할 수 있다" "ALTER TABLE" \
           "$(server "ALTER TABLE milestones ADD COLUMN twin_probe TEXT")"
    server "ALTER TABLE milestones DROP COLUMN twin_probe" >/dev/null

    say "bulk-ownership — 그 상태에서 부트스트랩이 도는가"
    step "users 소유자가 momens_server 라 REFERENCES GRANT 가 필요 없어진다"
    step "users REFERENCES 명시 권한: $(root "select has_table_privilege('momens_server','users','REFERENCES')") (소유자라 t)"
    expect "심기 성공" "COMMIT" "$(op_file "$bootstrap_sql")"
    start_app "$toggles $lock_opt"
    local result; result="$(await_app 180)"
    if [[ "$result" == started ]]; then ok "선행 조건이 소유권 하나로 줄어든다"
    else bad "기동 실패 ($result)"; grep -ioE "permission denied for [a-z ]+[a-z_]+|must be owner of table [a-z_]+" "$app_log" | head -3 | sed 's/^/         /'; fi
    step "이력 총 $(server "select count(*) from flyway_schema_history") 행, 실패 $(server "select count(*) from flyway_schema_history where not success") 건"
    stop_app
}

scenario_no_search_path() {
    say "no-search-path — momens_server 가 extensions 를 못 보는 경우"
    step "Supabase 는 uuid-ossp 를 extensions 에 두는데 momens_server 의 search_path 에는 없다."
    reset_db
    prereq_operator_set_role
    prereq_tasks_ownership
    prereq_users_references
    step "search_path: $(server 'show search_path')  ·  extensions USAGE: $(root "select has_schema_privilege('momens_server','extensions','USAGE')")"

    expect "심기 성공" "COMMIT" "$(op_file "$bootstrap_sql")"

    start_app "$toggles $lock_opt"
    local result; result="$(await_app 180)"
    if [[ "$result" == started ]]; then bad "기동에 성공했습니다. extensions 없이 통과하면 안 됩니다"
    else ok "기동 실패 ($result)"; fi
    expect "uuid_generate_v4() does not exist" "function uuid_generate_v4() does not exist" "$(cat "$app_log")"
    step "죽은 위치: $(grep -oE 'V20260810090000__[a-z_]+\.sql' "$app_log" | head -1)"
    step "실패 후 새 테이블: $(server "select count(*) from pg_tables where tablename in ('signals','outbox_events','user_identities')") 개 (group=true 면 0)"
    stop_app

    say "no-search-path — 처방 두 줄을 주면"
    prereq_extensions_search_path
    step "search_path: $(server 'show search_path')  ·  uuid_generate_v4(): $(server 'select uuid_generate_v4()' | tail -1)"
    start_app "$toggles $lock_opt"
    result="$(await_app 180)"
    if [[ "$result" == started ]]; then ok "USAGE + search_path 둘 다 있어야 통과한다"
    else bad "기동 실패 ($result)"; fi
    stop_app
}

scenario_ownership_reverted() {
    say "ownership-reverted — 부트스트랩 성공 후 tasks 소유권을 되돌리면"
    step "prod 의 GRANT 18 개는 tasks 를 뺐다. 런타임 DML 이 소유권에만 매달린 상태다."
    reset_db
    grant_ddl_prerequisites
    op_file "$bootstrap_sql" >/dev/null 2>&1
    start_app "$toggles $lock_opt"
    local result; result="$(await_app 180)"
    [[ "$result" == started ]] && ok "부트스트랩 성공" || bad "부트스트랩 실패 ($result)"
    stop_app

    step "이전 직후 ACL: $(root "select array_to_string(relacl,' ') from pg_class where relname='tasks'" | tr ' ' '\n' | grep momens_server | paste -sd' ' -)"

    root "ALTER TABLE tasks OWNER TO sb_postgres" >/dev/null
    step "소유권 되돌림 → 소유자: $(root "select tableowner from pg_tables where tablename='tasks'")"
    local sel; sel="$(root "select has_table_privilege('momens_server','tasks','SELECT')")"
    if [[ "$sel" == "f" ]]; then
        ok "momens_server 가 tasks 를 못 읽는다 — 사전 GRANT 는 이전을 견디지 못한다"
    else
        bad "SELECT 가 남아 있습니다. 사전 GRANT 가 이전을 견딘다면 롤백 절차를 줄일 수 있습니다"
    fi
    expect "실제 조회가 거부된다" "permission denied for table tasks" \
           "$(root "SET ROLE momens_server; SELECT count(*) FROM tasks" 2>&1 || true)"

    # 요점은 여기다. ddl-auto=validate 는 카탈로그만 보고 DML 권한을 보지 않으므로 **기동이
    # 성공한다.** 손실은 배포에서 잡히지 않고 첫 요청에서 터진다.
    start_app ""
    result="$(await_app 180)"
    if [[ "$result" == started ]]; then
        ok "기동은 성공한다 — validate 가 DML 권한을 보지 않아 배포에서 안 잡힌다"
    else
        bad "기동이 실패했습니다. 그렇다면 배포가 이 손실을 잡아준다는 뜻입니다 ($result)"
    fi
    stop_app

    say "ownership-reverted — 되돌릴 때 GRANT 를 다시 주면"
    root "GRANT SELECT, INSERT, UPDATE, DELETE ON tasks TO momens_server" >/dev/null
    step "momens_server 의 tasks SELECT: $(root "select has_table_privilege('momens_server','tasks','SELECT')")"
    start_app ""
    result="$(await_app 180)"
    if [[ "$result" == started ]]; then ok "기동 성공 — 롤백 절차에 이 한 줄이 있어야 한다"
    else bad "기동 실패 ($result)"; grep -ioE "permission denied for table [a-z_]+" "$app_log" | head -3 | sed 's/^/         /'; fi
    stop_app
}

scenario_lock() {
    say "lock — 레거시가 tasks 를 ACCESS EXCLUSIVE 로 잡고 있는 경우"
    reset_db
    grant_ddl_prerequisites
    op_file "$bootstrap_sql" >/dev/null 2>&1

    # 레거시 트래픽 대역. 60 초간 tasks 를 잡고 있는다.
    docker exec -d "$container" psql -U postgres -d "$db" -c \
        "BEGIN; LOCK TABLE tasks IN ACCESS EXCLUSIVE MODE; SELECT pg_sleep(60); COMMIT;"
    sleep 3
    step "경합 세션: $(root "select count(*) from pg_locks l join pg_class c on c.oid=l.relation where c.relname='tasks' and l.mode='AccessExclusiveLock'") 개"

    local t0 t1
    t0="$(date +%s)"
    start_app "$toggles $lock_opt"
    local result; result="$(await_app 120)"
    t1="$(date +%s)"

    if [[ "$result" == started ]]; then
        bad "락을 뚫고 기동했습니다. lock_timeout 이 동작하지 않았거나 락이 이미 풀렸습니다"
    else
        expect "lock_timeout 으로 실패" "canceling statement due to lock timeout" "$(cat "$app_log")"
        if [[ $((t1 - t0)) -lt 60 ]]; then ok "락이 풀리기 전에 포기했다"
        else bad "락이 풀릴 때까지 기다렸다 ($((t1 - t0))초)"; fi
    fi

    # 프로세스 시작부터 재면 JVM·Spring 기동이 섞인다. 절차에 적을 상한은 **Flyway 가 DB 를
    # 잡은 뒤 포기까지** 이므로 로그 타임스탬프로 구간을 분리한다. lock_timeout 이 몇 번
    # 소진되는지도 함께 센다 — 두 번이면 창구의 wall-clock 예산이 두 배가 된다.
    timeline "$t0" "$t1"
    step "실패 후 새 테이블: $(server "select count(*) from pg_tables where tablename in ('signals','outbox_events','user_identities')") 개"
    stop_app
    docker exec -i "$container" psql -U postgres -d postgres -q -Atc \
        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$db' AND query LIKE '%pg_sleep%'" >/dev/null 2>&1
}

scenario_checksum() {
    say "checksum — 심은 체크섬 하나가 파일과 다른 경우"
    reset_db
    grant_ddl_prerequisites
    op_file "$bootstrap_sql" >/dev/null 2>&1

    local target before
    target="20260821110000"   # workspace invitations mirror. 심기 목록 한가운데다.
    before="$(server "select checksum from flyway_schema_history where version='$target'")"
    root "UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '$target'" >/dev/null
    step "version $target 체크섬 $before → $((before + 1))"

    start_app "$toggles $lock_opt"
    local result; result="$(await_app 180)"
    if [[ "$result" == started ]]; then bad "기동에 성공했습니다. 체크섬 불일치를 놓쳤습니다"
    else ok "기동 실패 ($result)"; fi
    expect "checksum mismatch 로 죽는다" "checksum mismatch" "$(cat "$app_log")"
    step "실패 후 새 테이블: $(server "select count(*) from pg_tables where tablename in ('signals','outbox_events','user_identities')") 개 (스키마 무변경이어야 한다)"
    stop_app

    say "checksum — --verify 가 이것을 잡는가"
    local verify_out
    verify_out="$(PGPASSWORD=momens_server "$repo_root/scripts/prod-flyway-bootstrap.sh" --verify \
        "postgresql://momens_server@127.0.0.1:$port/$db" 2>&1)"
    if grep -qF "체크섬이 다릅니다" <<<"$verify_out" && grep -qF "$target" <<<"$verify_out"; then
        ok "--verify 가 불일치를 version 단위로 지목한다"
        grep -A1 "체크섬이 다릅니다" <<<"$verify_out" | sed 's/^/         /'
    else
        bad "--verify 가 통과시켰습니다"
        printf '%s\n' "$verify_out" | head -15 | sed 's/^/         /'
    fi
}

# --- 실행 -------------------------------------------------------------------

case "${1:-all}" in
    baseline)      scenario_baseline ;;
    no-ownership)  scenario_no_ownership ;;
    no-references) scenario_no_references ;;
    no-set-option) scenario_no_set_option ;;
    bulk-ownership) scenario_bulk_ownership ;;
    no-search-path) scenario_no_search_path ;;
    ownership-reverted) scenario_ownership_reverted ;;
    history-owner) scenario_history_owner ;;
    lock)          scenario_lock ;;
    checksum)      scenario_checksum ;;
    all)
        scenario_baseline
        scenario_no_ownership
        scenario_no_references
        scenario_no_set_option
        scenario_no_search_path
        scenario_bulk_ownership
        scenario_ownership_reverted
        scenario_history_owner
        scenario_lock
        scenario_checksum
        ;;
    *) echo "알 수 없는 시나리오: $1" >&2; exit 1 ;;
esac

printf '\n\033[1m== 결과 ==\033[0m\n   통과 %d · 실패 %d\n\n' "$pass" "$fail"
[[ "$fail" -eq 0 ]]
