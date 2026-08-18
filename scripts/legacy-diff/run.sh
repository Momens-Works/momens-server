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

cleanup() {
  [[ -n "$server_pid" ]] && kill "$server_pid" 2>/dev/null || true
  if [[ "$keep" != "1" ]]; then
    docker compose -f "${here}/compose.yml" down -v >/dev/null 2>&1 || true
  else
    echo "KEEP=1 이라 스택을 남깁니다. 정리: docker compose -f ${here}/compose.yml down -v"
  fi
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
if [[ -z "$(docker images -q momens-api:legacy-diff)" || "${REBUILD:-0}" == "1" ]]; then
  # momens-api 는 momens-proto 를 서브모듈로 vendoring 합니다. 비어 있으면 빌드가 실패합니다.
  git -C "$api_dir" submodule update --init third_party/momens-proto
  docker build -t momens-api:legacy-diff -f "${api_dir}/Dockerfile" "$api_dir"
else
  echo "momens-api:legacy-diff 재사용 (다시 빌드하려면 REBUILD=1)"
fi

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
jar="$(ls "${repo_root}"/app/build/libs/*.jar 2>/dev/null | grep -v plain | head -1 || true)"
if [[ -z "$jar" || "${REBUILD_SERVER:-0}" == "1" ]]; then
  (cd "$repo_root" && ./gradlew --quiet bootJar)
  jar="$(ls "${repo_root}"/app/build/libs/*.jar | grep -v plain | head -1)"
fi
java -jar "$jar" --spring.profiles.active=local > "${here}/.server.log" 2>&1 &
server_pid=$!
for _ in $(seq 90); do
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
"${here}/diff.sh" \
  --legacy-base "http://localhost:${MOMENS_DIFF_LEGACY_PORT}" \
  --server-base "http://localhost:${MOMENS_DIFF_SERVER_PORT}" \
  "$@"
