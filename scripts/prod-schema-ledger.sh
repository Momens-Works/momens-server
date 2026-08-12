#!/usr/bin/env bash
#
# prod 스키마 반영 대장 (MOM-0839).
#
# 각 마이그레이션 첫 줄의 `-- prod-schema:` 헤더를 정본으로 읽어 대장 문서를 생성하고,
# CI에서 헤더 누락과 미해결 항목을 검사한다. 규약은 docs/rules/persistence.md 를 본다.
#
# 사용법:
#   prod-schema-ledger.sh              헤더 상태를 표로 출력한다
#   prod-schema-ledger.sh --write      대장 문서를 다시 생성한다
#   prod-schema-ledger.sh --check      헤더 규약 위반과 대장 문서 최신 여부를 검사한다
#   prod-schema-ledger.sh --release-check  미반영(required/pending) 항목이 남아 있으면 실패한다

set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ledger_doc="docs/prod-schema-ledger.md"

# 헤더 문법. mirror 는 참조가 없고, 나머지 셋은 근거 참조가 필수다.
# prod 반영을 수행하는 저장소는 스키마 소유자에 따라 갈린다. 레거시 소유는 momens-api,
# worker가 생산하는 테이블은 momens-worker 마이그레이션이 될 수 있다(예: signals, ADR-0007).
header_pattern='^-- prod-schema: (mirror|required MOM-[0-9]+|(pending|applied) (momens-api|momens-worker)#[0-9]+)$'

# 마이그레이션은 기능 모듈(modules/<이름>)과 공유 모듈(common)이 함께 소유하므로 저장소 전체를
# 훑는다. 정렬 기준은 경로가 아니라 파일명(= 타임스탬프 버전)이다. 모듈 깊이가 다르기 때문이다.
migration_files() {
    find "$repo_root" -type d \( -name build -o -name .git -o -name .gradle-work \) -prune -o \
        -path '*/src/main/resources/db/migration/V*.sql' -print \
        | sed "s|^$repo_root/||" \
        | awk -F/ '{ print $NF "\t" $0 }' \
        | sort \
        | cut -f2-
}

# 각 파일을 "상태<TAB>참조<TAB>모듈<TAB>경로" 로 변환한다. 헤더가 규약을 벗어나면 상태가 invalid 다.
scan() {
    local file header status reference module
    migration_files | while IFS= read -r file; do
        # 워킹 트리가 CRLF로 남아 있는 경우에도 헤더를 같게 읽는다(.gitattributes 는 LF로 정규화한다).
        header="$(head -n 1 "$repo_root/$file" | tr -d '\r')"
        module="$(basename "${file%%/src/main/resources/db/migration/*}")"
        if [[ "$header" =~ $header_pattern ]]; then
            status="$(printf '%s' "${BASH_REMATCH[1]}" | cut -d' ' -f1)"
            reference="$(printf '%s' "${BASH_REMATCH[1]}" | cut -s -d' ' -f2)"
        else
            status="invalid"
            reference=""
        fi
        # 탭은 IFS 공백이라 빈 필드가 읽는 쪽에서 사라진다. 참조가 없으면 자리표시자를 넣는다.
        reference="${reference:--}"
        printf '%s\t%s\t%s\t%s\n' "$status" "$reference" "$module" "$file"
    done
}

render_section() {
    local scanned="$1" wanted="$2" heading="$3" note="$4"
    local rows count
    rows="$(printf '%s\n' "$scanned" | awk -F'\t' -v s="$wanted" '$1 == s')"
    count="$(printf '%s' "$rows" | grep -c . )"

    printf '\n## %s — %s건\n' "$heading" "$count"
    [[ -n "$note" ]] && printf '\n%s\n' "$note"

    if [[ "$count" -eq 0 ]]; then
        printf '\n없습니다.\n'
        return
    fi

    printf '\n| 마이그레이션 | 모듈 | 근거 |\n| --- | --- | --- |\n'
    printf '%s\n' "$rows" | while IFS=$'\t' read -r _ reference module file; do
        printf '| `%s` | `%s` | %s |\n' "$(basename "$file")" "$module" "$reference"
    done
}

render_ledger() {
    local scanned="$1"

    cat <<'HEADER'
# prod 스키마 반영 대장

<!--
이 파일은 scripts/prod-schema-ledger.sh --write 가 생성합니다. 직접 수정하지 마세요.
정본은 각 마이그레이션 첫 줄의 `-- prod-schema:` 헤더입니다.
-->

prod는 레거시 `momens-api`와 공유 DB를 쓰는 전환기라 이 서버의 Flyway가 꺼져 있고
`ddl-auto: validate`로 매핑만 검증합니다([데이터](rules/persistence.md)). 따라서 서버가 추가한
신규 스키마는 **반영 담당 저장소**의 마이그레이션으로 prod에 반영해야 하고, 반영되지 않으면 매핑
검증에 실패해 **애플리케이션이 기동하지 않습니다.**

담당 저장소는 스키마 소유자에 따라 갈립니다. 레거시가 소유한 스키마는 `momens-api`가 반영하지만,
worker가 생산하는 테이블(`signals` 계열, ADR-0007)의 반영 위치는 아직 확정되지 않았습니다.

이 문서는 그 반영 상태를 마이그레이션 단위로 추적합니다.
HEADER

    render_section "$scanned" "required" "미반영" \
        "prod에 반영해야 하고 아직 반영 PR이 없는 항목입니다. 릴리스 PR에서 차단됩니다."
    render_section "$scanned" "pending" "반영 중" \
        "반영 PR이 열려 있고 아직 prod에 적용되지 않은 항목입니다. 릴리스 PR에서 차단됩니다."
    render_section "$scanned" "applied" "반영 완료" ""
    render_section "$scanned" "mirror" "레거시 소유 미러" \
        "레거시가 이미 소유한 스키마라 prod 반영 의무가 없습니다. 이 서버는 local/test용 미러만 만듭니다."
}

check_headers() {
    local scanned="$1" invalid failed=0
    invalid="$(printf '%s\n' "$scanned" | awk -F'\t' '$1 == "invalid" { print $4 }')"

    if [[ -n "$invalid" ]]; then
        failed=1
        echo "::error::다음 마이그레이션의 첫 줄에 유효한 prod-schema 헤더가 없습니다."
        printf '%s\n' "$invalid" | sed 's/^/  /'
        cat <<'USAGE'

  첫 줄은 다음 중 하나여야 합니다.
    -- prod-schema: mirror
    -- prod-schema: required MOM-<번호>
    -- prod-schema: pending <momens-api|momens-worker>#<PR번호>
    -- prod-schema: applied <momens-api|momens-worker>#<PR번호>

  규약은 docs/rules/persistence.md 를 보세요.
USAGE
    fi
    return "$failed"
}

check_ledger_current() {
    local scanned="$1" generated
    generated="$(render_ledger "$scanned")"
    if ! diff -q <(printf '%s\n' "$generated") "$repo_root/$ledger_doc" >/dev/null 2>&1; then
        echo "::error::$ledger_doc 가 최신이 아닙니다. scripts/prod-schema-ledger.sh --write 로 다시 생성하세요."
        return 1
    fi
    return 0
}

check_release() {
    local scanned="$1" unresolved
    unresolved="$(printf '%s\n' "$scanned" | awk -F'\t' '$1 == "required" || $1 == "pending" { print $1 "\t" $4 }')"

    if [[ -n "$unresolved" ]]; then
        echo "::error::prod에 반영되지 않은 마이그레이션이 남아 있어 릴리스할 수 없습니다."
        printf '%s\n' "$unresolved" | sed 's/^/  /'
        echo "  반영을 끝내고 헤더를 applied 로 바꾼 뒤 다시 시도하세요."
        return 1
    fi
    echo "미반영 마이그레이션 없음"
    return 0
}

main() {
    local scanned status=0
    if ! scanned="$(scan)"; then
        echo "::error::마이그레이션 파일 스캔에 실패했습니다." >&2
        return 1
    fi

    case "${1:---list}" in
        --list)
            printf '%s\n' "$scanned" | awk -F'\t' '{ printf "%-10s %-24s %s\n", $1, $2, $4 }'
            ;;
        --write)
            render_ledger "$scanned" > "$repo_root/$ledger_doc"
            echo "생성: $ledger_doc"
            ;;
        --check)
            check_headers "$scanned" || status=1
            # 헤더가 깨진 상태에서는 대장 비교 결과가 의미가 없으므로 건너뛴다.
            [[ "$status" -eq 0 ]] && { check_ledger_current "$scanned" || status=1; }
            [[ "$status" -eq 0 ]] && echo "prod-schema 헤더와 대장 OK"
            ;;
        --release-check)
            check_headers "$scanned" || status=1
            [[ "$status" -eq 0 ]] && { check_release "$scanned" || status=1; }
            ;;
        *)
            echo "알 수 없는 옵션: $1" >&2
            echo "사용법: $(basename "$0") [--list|--write|--check|--release-check]" >&2
            status=2
            ;;
    esac
    return "$status"
}

main "$@"
