#!/usr/bin/env bash
# 레거시 차등 비교 하네스를 한 번에 실행합니다(MOM-0877).
#
#   scripts/legacy-diff/run.sh              # 전체 실행 후 정리
#   KEEP=1 scripts/legacy-diff/run.sh       # 스택을 남겨 반복 실행
#   scripts/legacy-diff/run.sh --only H020-member
#
# 레거시 저장소 위치는 MOMENS_API_DIR 로 지정합니다. 기본값은 <repo>/../momens-api 입니다.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${here}/../.." && pwd)"

set -a
# shellcheck disable=SC1091
source "${here}/harness.conf"
MOMENS_AUTH_JWT_SECRET="$MOMENS_DIFF_JWT_SECRET"
set +a

api_dir="${MOMENS_API_DIR:-${repo_root}/../momens-api}"
keep="${KEEP:-0}"
server_pid=""

log() { printf '\n\033[1m%s\033[0m\n' "$*"; }

# KEEP=1 은 신규 서버까지 남깁니다. write 케이스는 반복 실행이 잦은데 서버를 죽이면 --only 한 번마다
# Gradle 빌드와 부팅을 다시 기다리게 됩니다(MOM-0882).
cleanup() {
  if [[ "$keep" == "1" ]]; then
    echo "KEEP=1 이라 스택과 신규 서버를 남깁니다."
    echo "  반복 실행: ${here}/diff.sh --local-stack --only <케이스>"
    if [[ -n "$server_pid" ]]; then
      echo "  정리: kill ${server_pid} && docker compose -f ${here}/compose.yml down -v"
      echo "  남은 서버를 정리하지 않으면 다음 run.sh 가 포트 충돌로 멈춥니다."
    else
      echo "  정리: docker compose -f ${here}/compose.yml down -v"
    fi
    return
  fi
  [[ -n "$server_pid" ]] && kill "$server_pid" 2>/dev/null || true
  docker compose -f "${here}/compose.yml" down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

for tool in docker curl jq openssl; do
  command -v "$tool" >/dev/null || { echo "$tool 가 필요합니다." >&2; exit 1; }
done

if [[ ! -d "$api_dir" ]]; then
  echo "레거시 저장소를 찾지 못했습니다: ${api_dir}" >&2
  echo "MOMENS_API_DIR=/path/to/momens-api 로 지정하세요." >&2
  exit 1
fi

log "1/6 레거시 이미지 준비"
# 이미지 태그에 레거시 체크아웃의 revision 을 박습니다. 이름만 보고 재사용하면 다른 baseline 에서
# 만든 이미지로 비교하게 되는데, 그 오염은 결과에 드러나지 않습니다. 태그가 다르면 자동으로
# 다시 빌드되므로 "레거시는 동결이라 괜찮다"는 가정에 의존하지 않습니다.
legacy_rev="$(git -C "$api_dir" rev-parse --short HEAD)"
legacy_tag="momens-api:legacy-diff-${legacy_rev}"
legacy_dirty=0
git -C "$api_dir" diff --quiet || legacy_dirty=1

if [[ "$legacy_dirty" -eq 1 || -z "$(docker images -q "$legacy_tag")" || "${REBUILD:-0}" == "1" ]]; then
  [[ "$legacy_dirty" -eq 1 ]] && echo "레거시 작업 트리가 깨끗하지 않아 다시 빌드합니다."
  # momens-api 는 momens-proto 를 서브모듈로 vendoring 합니다. 비어 있으면 빌드가 실패합니다.
  git -C "$api_dir" submodule update --init third_party/momens-proto
  docker build -t "$legacy_tag" -f "${api_dir}/Dockerfile" "$api_dir"
else
  echo "${legacy_tag} 재사용 (다시 빌드하려면 REBUILD=1)"
fi
# compose 는 고정 이름을 참조하므로 이번 revision 의 이미지를 그 이름에 붙입니다.
docker tag "$legacy_tag" momens-api:legacy-diff

log "2/6 컨테이너 기동"
docker compose -f "${here}/compose.yml" up -d

log "3/6 레거시 기동 대기 (마이그레이션 포함)"
for _ in $(seq 60); do
  curl -fsS "http://localhost:${MOMENS_DIFF_LEGACY_PORT}/health" >/dev/null 2>&1 && break
  sleep 1
done
curl -fsS "http://localhost:${MOMENS_DIFF_LEGACY_PORT}/health" >/dev/null \
  || { echo "레거시가 뜨지 않았습니다."; docker compose -f "${here}/compose.yml" logs legacy-api | tail -30; exit 1; }

log "4/6 신규 서버 기동 (Flyway 로 스키마 생성)"
# 항상 다시 만듭니다. 이 하네스의 주 사용처가 "방금 고친 내 구현을 레거시와 대조하는 것"이라,
# 기존 JAR 을 재사용하면 정확히 그 상황에서 이전 코드의 결과를 보게 됩니다. Gradle 증분 빌드라
# 변경이 없으면 몇 초입니다.
# 이미 떠 있는 서버가 포트를 쥐고 있으면 여기서 멈춥니다. 그대로 진행하면 방금 띄운 서버는 바인딩에
# 실패해 죽고 헬스 체크는 옛 서버가 응답해 통과합니다. 아래 기동 대기 루프의 생존 확인만으로는 이
# 경우를 못 잡습니다. 옛 서버가 즉시 응답해 루프가 첫 회차에 빠져나가기 때문입니다.
if curl -fsS "http://localhost:${MOMENS_DIFF_SERVER_PORT}/actuator/health" >/dev/null 2>&1; then
  echo "포트 ${MOMENS_DIFF_SERVER_PORT} 에 이미 서버가 떠 있습니다. KEEP=1 로 남긴 서버일 수 있습니다." >&2
  echo "정리 후 다시 실행하세요: kill \$(lsof -ti :${MOMENS_DIFF_SERVER_PORT})" >&2
  exit 1
fi

(cd "$repo_root" && ./gradlew --quiet bootJar)
jar="$(ls "${repo_root}"/app/build/libs/*.jar | grep -v plain | head -1)"
java -jar "$jar" --spring.profiles.active=local > "${here}/.server.log" 2>&1 &
server_pid=$!
# 프로세스 생존을 함께 확인합니다. KEEP=1 으로 남은 이전 서버가 포트를 쥐고 있으면 방금 띄운
# 서버는 바인딩에 실패해 즉시 죽는데, 헬스 체크는 살아있는 옛 서버가 응답해 통과합니다. 그러면
# 바로 위 주석이 경계한 "이전 코드의 결과를 보는" 상황이 JAR 이 아니라 프로세스 층에서 되살아나고,
# 출력에는 아무 이상 신호가 없습니다.
for _ in $(seq 90); do
  if ! kill -0 "$server_pid" 2>/dev/null; then
    echo "신규 서버 프로세스가 죽었습니다. 포트 ${MOMENS_DIFF_SERVER_PORT} 를 이전 실행이 쥐고 있을 수 있습니다."
    echo "로그: ${here}/.server.log"
    tail -30 "${here}/.server.log"
    exit 1
  fi
  curl -fsS "http://localhost:${MOMENS_DIFF_SERVER_PORT}/actuator/health" >/dev/null 2>&1 && break
  sleep 1
done
curl -fsS "http://localhost:${MOMENS_DIFF_SERVER_PORT}/actuator/health" >/dev/null \
  || { echo "신규 서버가 뜨지 않았습니다. 로그: ${here}/.server.log"; tail -30 "${here}/.server.log"; exit 1; }

log "5/6 픽스처 적용 (양쪽 DB 에 같은 행)"
docker compose -f "${here}/compose.yml" exec -T legacy-db \
  psql -q -v ON_ERROR_STOP=1 -U momens -d momens_legacy < "${here}/fixture.sql"
docker compose -f "${here}/compose.yml" exec -T server-db \
  psql -q -v ON_ERROR_STOP=1 -U momens -d momens_server < "${here}/fixture.sql"

log "6/6 차등 비교"
# --local-stack 은 이 스크립트가 띄운 일회용 compose 스택을 대상으로 한다는 선언입니다. write 케이스의
# 픽스처 되돌리기와 DB 기록 비교가 이 플래그에서만 동작합니다. dev 실서버를 가리킬 때는 diff.sh 를
# 직접 호출하며, 그 경로에서는 write 케이스가 실데이터를 건드리지 않도록 건너뜁니다.
"${here}/diff.sh" \
  --local-stack \
  --legacy-base "http://localhost:${MOMENS_DIFF_LEGACY_PORT}" \
  --server-base "http://localhost:${MOMENS_DIFF_SERVER_PORT}" \
  "$@"
