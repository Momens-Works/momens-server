#!/usr/bin/env bash
#
# OpenAPI 계약 변경 알림(MOM-0886).
#
# 두 시점의 OpenAPI 스냅샷을 비교해 클라이언트에 영향을 주는 변경만 모아 팀 채널로 전송한다.
#
# 파일 변경 여부만으로 알림을 보내지 않는다. 설명 문구만 수정해도 docs/spec/openapi.json이
# 변경되기 때문이다. 이러한 변경까지 알리면 불필요한 알림이 쌓여 대응이 필요한 변경을
# 놓칠 수 있다.
#
# 변경 판정 규칙은 직접 구현하지 않고 oasdiff의 changelog를 사용한다. 두 OpenAPI 3.x 문서를
# 비교해 클라이언트에 영향을 주는 변경만 반환하고, 각 항목에 영향도(level)를 부여한다.
# 영향도 3은 클라이언트가 기존 방식을 사용할 수 없게 되는 변경이고, 영향도 1은 참고가
# 필요한 변경이다.
#
# 사용법:
#   openapi-change-notify.sh <기준 스냅샷> <변경 스냅샷>
#
# 환경 변수:
#   PR_NUMBER PR_TITLE PR_URL   알림 본문에 포함한다. 값이 없으면 해당 줄을 생략한다.
#   DISCORD_WEBHOOK_URL         값이 없으면 전송하지 않고 본문만 표준 출력으로 내보낸다.
#   OASDIFF_RUNNER SEND_RUNNER  테스트에서 이 이름에 다른 함수를 지정해 docker와 curl을 실제로
#                               호출하지 않도록 한다.

set -euo pipefail

# 서드파티 이미지는 digest로 고정한다. 태그는 같은 이름으로 다른 이미지를 가리킬 수 있다.
readonly OASDIFF_IMAGE="tufin/oasdiff@sha256:6065c16a4c9ce12504752f444d4981091e58c2a35436fac90b649be47d833db3"

# 디스코드 메시지 본문은 최대 2,000자다. 머리말과 맺음말에 필요한 길이를 제외한 나머지를 변경 항목에 할당한다.
readonly CONTENT_LIMIT=2000

# 제외 안내문과 그룹 제목이 들어갈 자리다. 안내문은 제외된 항목이 생겨야 붙지만 그때는 이미 예산을
# 다 쓴 뒤라 미리 빼 두어야 한다. 그룹은 영향도 셋이 모두 나오는 경우를 기준으로 잡는다.
readonly RESERVED_TAIL=200

# docker 호출을 함수로 분리한다. 테스트에서는 OASDIFF_RUNNER에 다른 함수를 지정해 docker를 호출하지 않는다.
docker_oasdiff() {
    local base="$1" head="$2" mount
    # 두 스냅샷을 하나의 디렉터리에 모아 마운트한다. 호출하는 쪽에서 파일을 어느 경로에 두더라도 동일한 방식으로 실행할 수 있다.
    mount="$(mktemp -d)"
    cp "$base" "$mount/base.json"
    cp "$head" "$mount/head.json"
    docker run --rm -v "$mount:/specs:ro" "$OASDIFF_IMAGE" \
        changelog /specs/base.json /specs/head.json --format json
    rm -rf "$mount"
}

# 메시지 전송도 같은 이유로 분리한다. 테스트에서는 SEND_RUNNER에 다른 함수를 지정해 실제 메시지를 보내지 않는다.
curl_discord() {
    curl -fsS -X POST -H 'Content-Type: application/json' --data @- "$1" >/dev/null
}

# 변경 항목을 영향도 내림차순으로 묶어 본문을 생성한다. 글자 수 한도를 넘으면 해당 항목부터
# 제외하고 남은 건수를 알린다. 항목 단위로 제외하므로 문장이 중간에서 끊기지 않는다.
build_body() {
    local changes="$1" budget="$2"
    jq -r --argjson budget "$budget" '
        def level_name($l): if $l == 3 then "호환되지 않는 변경"
                       elif $l == 2 then "주의"
                       else "알림" end;
        # components 아래의 변경에는 스키마 추가나 삭제처럼 operation과 path가 없는 항목도 있다.
        def line:
            if (.path // "") == "" then "- \(.text)"
            else "- `\(.operation) \(.path)`: \(.text)" end;

        [ .[] | select(.level == 3) ] as $blocking
        | [ .[] | select(.level == 2) ] as $warning
        | [ .[] | select(.level == 1) ] as $info
        | [ $blocking, $warning, $info ]
        | map(select(length > 0))
        | map({ level: .[0].level, items: . })
        | reduce .[] as $group ({ text: "", used: 0, dropped: 0 };
            ("__" + level_name($group.level) + " " + ($group.items | length | tostring) + "건__\n") as $title
            | reduce $group.items[] as $item (
                . + { text: (.text + $title), used: (.used + ($title | length)) };
                ($item | line) as $rendered
                | if (.used + ($rendered | length)) <= $budget
                  then { text: (.text + $rendered + "\n"), used: (.used + ($rendered | length)), dropped: .dropped }
                  else { text: .text, used: .used, dropped: (.dropped + 1) }
                  end)
            | { text: (.text + "\n"), used: .used, dropped: .dropped })
        | .text + (if .dropped > 0 then "그 외 \(.dropped)건은 아래 PR에서 확인해 주세요.\n" else "" end)
    ' <<< "$changes"
}

main() {
    local base="${1:-}" head="${2:-}"

    [[ -n "$base" && -n "$head" ]] || {
        echo "사용법: openapi-change-notify.sh <기준 스냅샷> <변경 스냅샷>" >&2
        return 2
    }
    [[ -f "$base" && -f "$head" ]] || {
        echo "스냅샷 파일을 찾을 수 없습니다: $base $head" >&2
        return 2
    }

    local changes
    changes="$(${OASDIFF_RUNNER:-docker_oasdiff} "$base" "$head")"

    if [[ "$(jq 'length' <<< "$changes")" -eq 0 ]]; then
        echo "클라이언트가 대응해야 하는 변경이 없어 알림을 보내지 않습니다." >&2
        return 0
    fi

    local heading="**API 계약이 변경되었습니다**"
    local subject=""
    [[ -n "${PR_NUMBER:-}" ]] && subject="PR #${PR_NUMBER}"
    [[ -n "${PR_TITLE:-}" ]] && subject="${subject:+$subject }${PR_TITLE}"
    [[ -n "$subject" ]] && heading="${heading}"$'\n'"${subject}"

    local tail=""
    [[ -n "${PR_URL:-}" ]] && tail=$'\n\n'"<${PR_URL}>"

    # 항목에 쓸 수 있는 길이는 상한에서 머리말과 맺음말이 차지하는 만큼을 뺀 나머지다. 고정값을 두면
    # PR 제목이 길 때 합계가 상한을 넘어 완성된 본문을 문자 단위로 잘라야 하고, 그러면 항목이나
    # 링크가 중간에서 끊긴다.
    local budget=$(( CONTENT_LIMIT - ${#heading} - ${#tail} - RESERVED_TAIL ))

    local body content
    body="$(build_body "$changes" "$budget")"
    content="${heading}"$'\n\n'"${body}${tail}"

    if [[ -z "${DISCORD_WEBHOOK_URL:-}" ]]; then
        echo "웹훅 URL이 없어 전송하지 않고 본문만 출력합니다." >&2
        printf '%s\n' "$content"
        return 0
    fi

    jq -n --arg content "$content" '{ content: $content }' \
        | ${SEND_RUNNER:-curl_discord} "$DISCORD_WEBHOOK_URL"
}

# 테스트가 함수만 가져다 쓸 수 있도록 직접 실행일 때만 돈다.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
