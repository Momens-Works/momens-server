#!/usr/bin/env bash
# 같은 요청을 레거시 momens-api 와 신규 momens-server 에 보내고 응답을 대조합니다(MOM-0877).
# write 요청과 요청 이후의 DB 기록 대조를 함께 지원합니다(MOM-0882).
#
# 로컬 모드에서는 fixture.sql 이 양쪽 DB 에 같은 행을 심으므로 값까지 문자 그대로 같아야 합니다.
# 따라서 정규화는 JSON 키 정렬(jq -S) 하나뿐입니다. 키 순서는 JSON 의 계약이 아니지만 배열 순서와
# 값은 계약이므로 건드리지 않습니다.
#
# 예외가 write 케이스입니다. 값이 갱신되는 요청은 두 서버가 각자의 벽시계로 updated_at 을 찍고,
# no-op 요청은 레거시만 갱신합니다. 그래서 케이스가 cases.tsv 의 ignore 열로 "이 필드는 응답에서
# 빼고 본다"를 선언합니다. 무엇을 검증하지 않기로 했는지가 케이스 목록 한 줄에 보여야 리뷰에서
# 걸립니다.
#
# dev 실서버를 대상으로 할 때만 --normalize 로 UUID·타임스탬프를 자리표시자로 바꿉니다. 이 모드는
# 값 비교를 포기하는 대신 shape 비교만 남깁니다.
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

legacy_base="http://localhost:18080"
server_base="http://localhost:18081"
cases_file="${here}/cases.tsv"
cases_dir="${here}/cases"
only=""
normalize=0
local_stack=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --legacy-base) legacy_base="$2"; shift 2 ;;
    --server-base) server_base="$2"; shift 2 ;;
    --cases) cases_file="$2"; shift 2 ;;
    --only) only="$2"; shift 2 ;;
    --normalize) normalize=1; shift ;;
    --local-stack) local_stack=1; shift ;;
    -h|--help)
      sed -n '2,17p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
  esac
done

: "${MOMENS_DIFF_JWT_SECRET:?MOMENS_DIFF_JWT_SECRET 가 필요합니다 (harness.conf 참고)}"

# fixture.sql 의 사용자 키 -> UUID
user_uuid() {
  case "$1" in
    owner)    echo "00000000-0000-4000-8000-000000000001" ;;
    member)   echo "00000000-0000-4000-8000-000000000002" ;;
    stranger) echo "00000000-0000-4000-8000-000000000003" ;;
    nobody)   echo "00000000-0000-4000-8000-000000000004" ;;
    *)        echo "" ;;
  esac
}

legacy_psql() {
  docker compose -f "${here}/compose.yml" exec -T legacy-db \
    psql -X -v ON_ERROR_STOP=1 -U momens -d momens_legacy "$@"
}

server_psql() {
  docker compose -f "${here}/compose.yml" exec -T server-db \
    psql -X -v ON_ERROR_STOP=1 -U momens -d momens_server "$@"
}

# 양쪽 DB 를 픽스처 상태로 되돌립니다. fixture.sql 은 TRUNCATE ... CASCADE 로 시작해 멱등하므로
# 그대로 다시 적용하면 됩니다. 케이스 사이의 실행 순서 의존을 없애는 것이 목적이라, 되돌리기가
# 실패하면 이후 비교는 의미가 없으므로 호출 측에서 즉시 멈춥니다.
reset_fixture() {
  legacy_psql -q < "${here}/fixture.sql" >/dev/null || return 1
  server_psql -q < "${here}/fixture.sql" >/dev/null || return 1
}

# 응답 본문을 비교 가능한 형태로 만듭니다. JSON 이 아니면 원문을 그대로 둡니다.
canonicalize() {
  local body="$1" ignore="${2:-}"
  if ! printf '%s' "$body" | jq -e . >/dev/null 2>&1; then
    printf '%s' "$body"
    return
  fi
  printf '%s' "$body" | jq -S --arg ig "$ignore" --argjson norm "$normalize" '
    def drop($ks):
      walk(if type == "object"
           then with_entries(select(.key as $k | $ks | index($k) | not))
           else . end);
    def scrub:
      if type == "string" then
        if test("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$") then "<uuid>"
        elif test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T") then "<timestamp>"
        else . end
      elif type == "array" then map(scrub)
      elif type == "object" then with_entries(.value |= scrub)
      else . end;
    ($ig | split(",") | map(select(length > 0))) as $ks
    | (if ($ks | length) > 0 then drop($ks) else . end)
    | (if $norm == 1 then scrub else . end)'
}

# status 와 body 를 한 번의 curl 로 받습니다.
#
# curl 의 종료 상태를 그대로 넘깁니다. 연결 실패에도 -w 는 status 000 을 출력하므로, 실패를
# 삼키면 두 서버가 모두 죽었을 때 000 과 빈 body 가 일치해 전 케이스가 "동일"로 집계됩니다.
# 이 도구는 잘못된 확신을 없애려고 존재하므로 그 결과가 가장 나쁩니다.
call() {
  local base="$1" path="$2" token="$3" method="$4" body_file="$5"
  local args=(-sS --connect-timeout 5 --max-time 30 -o - -w '\n%{http_code}'
              -X "$method" "${base}${path}" -H 'API-Version: 1')
  [[ -n "$token" ]] && args+=(-H "Cookie: session_token=${token}")
  [[ -n "$body_file" ]] && args+=(-H 'Content-Type: application/json' --data-binary "@${body_file}")
  curl "${args[@]}"
}

pass=0
fail=0
skipped=0
# 직전 케이스가 DB 를 건드렸는지. write 뒤에 오는 read 케이스도 오염되므로 케이스 위치와 무관하게
# 되돌립니다. cases.tsv 의 행 순서에 의존하지 않기 위해서입니다.
dirty=0

while IFS=$'\t' read -r id as method legacy_path server_path ignore; do
  [[ -z "${id// }" || "${id:0:1}" == "#" ]] && continue
  [[ -n "$only" && "$id" != "$only" ]] && continue

  ignore="${ignore:-}"
  [[ "$ignore" == "-" ]] && ignore=""

  case_dir="${cases_dir}/${id}"
  body_file=""
  [[ -f "${case_dir}/body.json" ]] && body_file="${case_dir}/body.json"
  check_file=""
  [[ -f "${case_dir}/check.sql" ]] && check_file="${case_dir}/check.sql"

  is_write=0
  [[ "$method" != "GET" ]] && is_write=1

  # write 는 대상 DB 를 바꿉니다. dev 실서버를 가리킨 상태에서 돌면 실데이터를 건드리므로 막습니다.
  if [[ "$is_write" -eq 1 && "$local_stack" -eq 0 ]]; then
    echo "⏭  ${id}: write 케이스는 --local-stack 에서만 실행합니다 (${method})"
    skipped=$((skipped + 1))
    continue
  fi

  if [[ "$local_stack" -eq 1 && ( "$is_write" -eq 1 || "$dirty" -eq 1 ) ]]; then
    if ! reset_fixture; then
      echo "픽스처 되돌리기 실패: ${id}" >&2
      exit 1
    fi
    dirty=0
  fi

  token=""
  if [[ "$as" != "none" ]]; then
    uuid="$(user_uuid "$as")"
    if [[ -z "$uuid" ]]; then
      echo "⏭  ${id}: 알 수 없는 사용자 키 '${as}'"
      skipped=$((skipped + 1))
      continue
    fi
    token="$("${here}/mint-token.sh" "$uuid")"
  fi

  # 전송 실패는 케이스 실패가 아니라 비교 자체가 성립하지 않는 상태이므로 즉시 멈춥니다.
  if ! legacy_raw="$(call "$legacy_base" "$legacy_path" "$token" "$method" "$body_file")"; then
    echo "레거시 요청 실패: ${method} ${legacy_base}${legacy_path}" >&2
    exit 1
  fi
  if ! server_raw="$(call "$server_base" "$server_path" "$token" "$method" "$body_file")"; then
    echo "신규 서버 요청 실패: ${method} ${server_base}${server_path}" >&2
    exit 1
  fi
  [[ "$is_write" -eq 1 ]] && dirty=1

  legacy_status="${legacy_raw##*$'\n'}"
  server_status="${server_raw##*$'\n'}"
  legacy_body="${legacy_raw%$'\n'*}"
  server_body="${server_raw%$'\n'*}"

  legacy_json="$(canonicalize "$legacy_body" "$ignore")"
  server_json="$(canonicalize "$server_body" "$ignore")"

  echo "── ${id}  (as ${as})"
  echo "   legacy  ${legacy_status}  ${method} ${legacy_path}"
  echo "   server  ${server_status}  ${method} ${server_path}"
  [[ -n "$ignore" ]] && echo "   응답 무시 필드: ${ignore}"

  body_diff="$(diff -u <(printf '%s\n' "$legacy_json") <(printf '%s\n' "$server_json") | tail -n +3)"

  # DB 기록 비교. 응답만 같고 기록이 다른 경우를 잡는 것이 write 케이스의 핵심이라, 케이스가
  # check.sql 을 소유하고 양쪽 DB 에 그대로 실행합니다. 안 볼 컬럼은 SELECT 목록에서 빼면
  # 되므로 응답과 달리 별도의 무시 목록이 없습니다.
  db_diff=""
  db_checked=0
  if [[ -n "$check_file" ]]; then
    if [[ "$local_stack" -eq 0 ]]; then
      echo "   ⏭ DB 비교는 --local-stack 에서만 합니다"
    else
      if ! legacy_rows="$(legacy_psql --csv < "$check_file")"; then
        echo "레거시 DB 조회 실패: ${check_file}" >&2
        exit 1
      fi
      if ! server_rows="$(server_psql --csv < "$check_file")"; then
        echo "신규 DB 조회 실패: ${check_file}" >&2
        exit 1
      fi
      db_checked=1
      db_diff="$(diff -u <(printf '%s\n' "$legacy_rows") <(printf '%s\n' "$server_rows") | tail -n +3)"
    fi
  fi

  if [[ "$legacy_status" == "$server_status" && -z "$body_diff" && -z "$db_diff" ]]; then
    [[ "$db_checked" -eq 1 ]] && echo "   ✓ 동일 (응답·DB)" || echo "   ✓ 동일"
    pass=$((pass + 1))
  else
    [[ "$legacy_status" != "$server_status" ]] && echo "   ✗ status 차이: ${legacy_status} → ${server_status}"
    if [[ -n "$body_diff" ]]; then
      echo "   ✗ body 차이 (- 레거시 / + 신규)"
      printf '%s\n' "$body_diff" | sed 's/^/     /'
    fi
    if [[ -n "$db_diff" ]]; then
      echo "   ✗ DB 기록 차이 (- 레거시 / + 신규)"
      printf '%s\n' "$db_diff" | sed 's/^/     /'
    fi
    fail=$((fail + 1))
  fi
  echo
done < "$cases_file"

echo "동일 ${pass} · 차이 ${fail} · 건너뜀 ${skipped}"
echo
echo "차이가 났다는 것 자체는 실패가 아닙니다. 계약 문서가 확정한 의도된 차이인지 대조하세요."
