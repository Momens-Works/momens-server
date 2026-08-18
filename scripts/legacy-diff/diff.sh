#!/usr/bin/env bash
# 같은 요청을 레거시 momens-api 와 신규 momens-server 에 보내고 응답을 대조합니다(MOM-0877).
#
# 로컬 모드에서는 fixture.sql 이 양쪽 DB 에 같은 행을 심으므로 값까지 문자 그대로 같아야 합니다.
# 따라서 정규화는 JSON 키 정렬(jq -S) 하나뿐입니다. 키 순서는 JSON 의 계약이 아니지만 배열 순서와
# 값은 계약이므로 건드리지 않습니다.
#
# dev 실서버를 대상으로 할 때만 --normalize 로 UUID·타임스탬프를 자리표시자로 바꿉니다. 이 모드는
# 값 비교를 포기하는 대신 shape 비교만 남깁니다.
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

legacy_base="http://localhost:18080"
server_base="http://localhost:18081"
cases_file="${here}/cases.tsv"
only=""
normalize=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --legacy-base) legacy_base="$2"; shift 2 ;;
    --server-base) server_base="$2"; shift 2 ;;
    --cases) cases_file="$2"; shift 2 ;;
    --only) only="$2"; shift 2 ;;
    --normalize) normalize=1; shift ;;
    -h|--help)
      sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
  esac
done

: "${MOMENS_DIFF_JWT_SECRET:?MOMENS_DIFF_JWT_SECRET 가 필요합니다 (harness.env 참고)}"

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

# 응답 본문을 비교 가능한 형태로 만듭니다. JSON 이 아니면 원문을 그대로 둡니다.
canonicalize() {
  local body="$1"
  if ! printf '%s' "$body" | jq -e . >/dev/null 2>&1; then
    printf '%s' "$body"
    return
  fi
  if [[ "$normalize" -eq 1 ]]; then
    printf '%s' "$body" | jq -S '
      def scrub:
        if type == "string" then
          if test("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$") then "<uuid>"
          elif test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T") then "<timestamp>"
          else . end
        elif type == "array" then map(scrub)
        elif type == "object" then with_entries(.value |= scrub)
        else . end;
      scrub'
  else
    printf '%s' "$body" | jq -S .
  fi
}

# status 와 body 를 한 번의 curl 로 받습니다.
#
# curl 의 종료 상태를 그대로 넘깁니다. 연결 실패에도 -w 는 status 000 을 출력하므로, 실패를
# 삼키면 두 서버가 모두 죽었을 때 000 과 빈 body 가 일치해 전 케이스가 "동일"로 집계됩니다.
# 이 도구는 잘못된 확신을 없애려고 존재하므로 그 결과가 가장 나쁩니다.
call() {
  local base="$1" path="$2" token="$3"
  local args=(-sS --connect-timeout 5 --max-time 30 -o - -w '\n%{http_code}'
              -X GET "${base}${path}" -H 'API-Version: 1')
  [[ -n "$token" ]] && args+=(-H "Cookie: session_token=${token}")
  curl "${args[@]}"
}

pass=0
fail=0
skipped=0

while IFS=$'\t' read -r id as method legacy_path server_path; do
  [[ -z "${id// }" || "${id:0:1}" == "#" ]] && continue
  [[ -n "$only" && "$id" != "$only" ]] && continue

  if [[ "$method" != "GET" ]]; then
    echo "⏭  ${id}: GET 외 method 는 아직 지원하지 않습니다 (${method})"
    skipped=$((skipped + 1))
    continue
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
  if ! legacy_raw="$(call "$legacy_base" "$legacy_path" "$token")"; then
    echo "레거시 요청 실패: ${method} ${legacy_base}${legacy_path}" >&2
    exit 1
  fi
  if ! server_raw="$(call "$server_base" "$server_path" "$token")"; then
    echo "신규 서버 요청 실패: ${method} ${server_base}${server_path}" >&2
    exit 1
  fi

  legacy_status="${legacy_raw##*$'\n'}"
  server_status="${server_raw##*$'\n'}"
  legacy_body="${legacy_raw%$'\n'*}"
  server_body="${server_raw%$'\n'*}"

  legacy_json="$(canonicalize "$legacy_body")"
  server_json="$(canonicalize "$server_body")"

  echo "── ${id}  (as ${as})"
  echo "   legacy  ${legacy_status}  ${method} ${legacy_path}"
  echo "   server  ${server_status}  ${method} ${server_path}"

  body_diff="$(diff -u <(printf '%s\n' "$legacy_json") <(printf '%s\n' "$server_json") | tail -n +3)"

  if [[ "$legacy_status" == "$server_status" && -z "$body_diff" ]]; then
    echo "   ✓ 동일"
    pass=$((pass + 1))
  else
    [[ "$legacy_status" != "$server_status" ]] && echo "   ✗ status 차이: ${legacy_status} → ${server_status}"
    if [[ -n "$body_diff" ]]; then
      echo "   ✗ body 차이 (- 레거시 / + 신규)"
      printf '%s\n' "$body_diff" | sed 's/^/     /'
    fi
    fail=$((fail + 1))
  fi
  echo
done < "$cases_file"

echo "동일 ${pass} · 차이 ${fail} · 건너뜀 ${skipped}"
echo
echo "차이가 났다는 것 자체는 실패가 아닙니다. 계약 문서가 확정한 의도된 차이인지 대조하세요."
