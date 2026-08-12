#!/usr/bin/env bash
#
# prod 운영 준비 대장 (MOM-0839, MOM-0841).
#
# 각 마이그레이션 첫 줄의 `-- prod-schema:` 헤더를 정본으로 읽어 대장 문서를 생성하고,
# prod 유효 설정의 기본값 없는 환경변수가 문서에 선언됐는지 검사한다.
# 규약은 docs/rules/persistence.md 와 docs/rules/configuration.md 를 본다.
#
# 사용법:
#   prod-schema-ledger.sh              헤더 상태를 표로 출력한다
#   prod-schema-ledger.sh --write      대장 문서를 다시 생성한다
#   prod-schema-ledger.sh --check      헤더·생성 구간과 prod 필수 설정 선언을 검사한다
#   prod-schema-ledger.sh --release-check  미반영(required/pending) 항목이 남아 있으면 실패한다

set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ledger_doc="docs/prod-schema-ledger.md"
schema_begin='<!-- BEGIN GENERATED: prod-schema -->'
schema_end='<!-- END GENERATED: prod-schema -->'
config_begin='<!-- BEGIN DECLARATION: prod-required-config -->'
config_end='<!-- END DECLARATION: prod-required-config -->'
prod_config_files=(
    "app/src/main/resources/application.yml"
    "app/src/main/resources/application-prod.yml"
)

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

render_schema_section() {
    local scanned="$1"

    cat <<'HEADER'
## 스키마 반영

prod는 레거시 `momens-api`와 공유 DB를 쓰는 전환기라 이 서버의 Flyway가 꺼져 있고
`ddl-auto: validate`로 매핑만 검증합니다([데이터](rules/persistence.md)). 따라서 서버가 추가한
신규 스키마는 **반영 담당 저장소**의 마이그레이션으로 prod에 반영해야 하고, 반영되지 않으면 매핑
검증에 실패해 **애플리케이션이 기동하지 않습니다.**

담당 저장소는 스키마 소유자에 따라 갈립니다. 레거시가 소유한 스키마는 `momens-api`가 반영하지만,
worker가 생산하는 테이블(`signals` 계열, ADR-0007)의 반영 위치는 아직 확정되지 않았습니다.

이 문서는 그 반영 상태를 마이그레이션 단위로 추적합니다. 미반영 한 줄은 **"이 파일을 그대로
옮긴다"가 아니라 "이 파일이 만드는 객체 중 prod에 없는 것이 있다"**는 뜻입니다. 한 파일이 여러
객체를 건드리고 그중 일부만 레거시에 없을 수 있으므로, 반영 범위는 반영 시점에 객체 단위로
대조해 정합니다.
HEADER

    render_section "$scanned" "required" "미반영" \
        "prod에 반영해야 하고 아직 반영 PR이 없는 항목입니다. 릴리스 PR에서 차단됩니다."
    render_section "$scanned" "pending" "반영 중" \
        "반영 PR이 열려 있고 아직 prod에 적용되지 않은 항목입니다. 릴리스 PR에서 차단됩니다."
    render_section "$scanned" "applied" "반영 완료" ""
    render_section "$scanned" "mirror" "레거시 소유 미러" \
        "레거시가 이미 소유한 스키마라 prod 반영 의무가 없습니다. 이 서버는 local/test용 미러만 만듭니다."
}

check_marker_pair() {
    local begin="$1" end="$2" label="$3" begin_count end_count begin_line end_line
    begin_count="$(grep -cFx "$begin" "$repo_root/$ledger_doc")"
    end_count="$(grep -cFx "$end" "$repo_root/$ledger_doc")"

    if [[ "$begin_count" -ne 1 || "$end_count" -ne 1 ]]; then
        echo "::error::$ledger_doc 의 $label 구간 표시는 시작과 끝이 각각 하나여야 합니다."
        return 1
    fi

    begin_line="$(grep -nFx "$begin" "$repo_root/$ledger_doc" | cut -d: -f1)"
    end_line="$(grep -nFx "$end" "$repo_root/$ledger_doc" | cut -d: -f1)"
    if [[ "$begin_line" -ge "$end_line" ]]; then
        echo "::error::$ledger_doc 의 $label 시작 표시는 끝 표시보다 앞에 있어야 합니다."
        return 1
    fi
}

extract_schema_section() {
    awk -v begin="$schema_begin" -v end="$schema_end" '
        $0 == begin { inside = 1; next }
        $0 == end { inside = 0; next }
        inside { print }
    ' "$repo_root/$ledger_doc"
}

write_schema_section() {
    local scanned="$1" ledger_path ledger_dir generated temporary status=0
    check_marker_pair "$schema_begin" "$schema_end" "prod 스키마 생성" || return 1

    ledger_path="$repo_root/$ledger_doc"
    ledger_dir="$(dirname "$ledger_path")"
    generated="$(mktemp)" || return 1
    temporary="$(mktemp "$ledger_dir/.${ledger_doc##*/}.XXXXXX")" \
        || { rm -f "$generated"; return 1; }
    cp -p "$ledger_path" "$temporary" || status=1
    [[ "$status" -eq 0 ]] && { render_schema_section "$scanned" > "$generated" || status=1; }

    if [[ "$status" -eq 0 ]]; then
        awk -v begin="$schema_begin" -v end="$schema_end" -v generated="$generated" '
            $0 == begin {
                print
                while ((getline line < generated) > 0) print line
                close(generated)
                inside = 1
                next
            }
            $0 == end { inside = 0; print; next }
            !inside { print }
        ' "$ledger_path" > "$temporary" || status=1
    fi

    if [[ "$status" -eq 0 ]]; then
        mv -f "$temporary" "$ledger_path" || status=1
    fi
    rm -f "$generated" "$temporary"
    return "$status"
}

strip_yaml_comments() {
    awk '
        {
            output = ""
            single_quoted = 0
            double_quoted = 0
            escaped = 0

            for (position = 1; position <= length($0); position++) {
                character = substr($0, position, 1)
                previous = position == 1 ? "" : substr($0, position - 1, 1)
                comment_start = character == "#" && (position == 1 || previous ~ /[[:space:]]/)

                if (escaped) {
                    output = output character
                    escaped = 0
                    continue
                }
                if (double_quoted && character == "\\") {
                    output = output character
                    escaped = 1
                    continue
                }
                if (!double_quoted && character == "\047") {
                    single_quoted = !single_quoted
                } else if (!single_quoted && character == "\"") {
                    double_quoted = !double_quoted
                } else if (!single_quoted && !double_quoted && comment_start) {
                    break
                }
                output = output character
            }
            print output
        }
    ' "$@"
}

scan_prod_required_config() {
    local files=() file
    for file in "${prod_config_files[@]}"; do
        if [[ ! -f "$repo_root/$file" ]]; then
            echo "필수 prod 설정 파일을 찾을 수 없습니다: $file" >&2
            return 1
        fi
        files+=("$repo_root/$file")
    done

    strip_yaml_comments "${files[@]}" \
        | grep -oE '\$\{[A-Z][A-Z0-9_]*\}' \
        | sed -E 's/^\$\{//; s/\}$//' \
        | sort -u
}

config_declarations() {
    awk -F'|' -v begin="$config_begin" -v end="$config_end" '
        function trim(value) {
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
            gsub(/`/, "", value)
            return value
        }
        $0 == begin { inside = 1; next }
        $0 == end { inside = 0; next }
        inside && /^\|/ {
            key = trim($2)
            location = trim($3)
            status = trim($4)
            if (key == "환경변수" || key ~ /^-+$/) next
            print key "\t" location "\t" status
        }
    ' "$repo_root/$ledger_doc"
}

check_config_declarations() {
    local required="$1" declarations invalid duplicate declared_keys missing extra failed=0

    check_marker_pair "$config_begin" "$config_end" "prod 필수 설정 선언" || return 1
    declarations="$(config_declarations)"

    invalid="$(printf '%s\n' "$declarations" | awk -F'\t' '
        $1 !~ /^[A-Z][A-Z0-9_]*$/ ||
        ($2 != "secret" && $2 != "configmap") ||
        ($3 != "required" && $3 != "applied")
    ')"
    if [[ -n "$invalid" ]]; then
        failed=1
        echo "::error::prod 필수 설정 선언은 환경변수, secret|configmap, required|applied 형식이어야 합니다."
        printf '%s\n' "$invalid" | sed 's/^/  /'
    fi

    duplicate="$(printf '%s\n' "$declarations" | cut -f1 | sort | uniq -d)"
    if [[ -n "$duplicate" ]]; then
        failed=1
        echo "::error::prod 필수 설정 선언에 중복 키가 있습니다."
        printf '%s\n' "$duplicate" | sed 's/^/  /'
    fi

    declared_keys="$(printf '%s\n' "$declarations" | cut -f1 | sort -u)"
    missing="$(comm -23 <(printf '%s\n' "$required") <(printf '%s\n' "$declared_keys"))"
    extra="$(comm -13 <(printf '%s\n' "$required") <(printf '%s\n' "$declared_keys"))"

    if [[ -n "$missing" ]]; then
        failed=1
        echo "::error::prod 유효 설정에 있지만 대장에 선언되지 않은 필수 환경변수가 있습니다."
        printf '%s\n' "$missing" | sed 's/^/  /'
    fi
    if [[ -n "$extra" ]]; then
        failed=1
        echo "::error::대장에 선언됐지만 prod 유효 설정에서 요구하지 않는 환경변수가 있습니다."
        printf '%s\n' "$extra" | sed 's/^/  /'
    fi

    [[ "$failed" -eq 0 ]] && echo "prod 필수 설정 선언 OK"
    return "$failed"
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
    check_marker_pair "$schema_begin" "$schema_end" "prod 스키마 생성" || return 1
    generated="$(render_schema_section "$scanned")"
    if ! diff -q <(printf '%s\n' "$generated") <(extract_schema_section) >/dev/null 2>&1; then
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
    local scanned required_config status=0
    if ! scanned="$(scan)"; then
        echo "::error::마이그레이션 파일 스캔에 실패했습니다." >&2
        return 1
    fi
    if ! required_config="$(scan_prod_required_config)"; then
        echo "::error::prod 필수 설정 스캔에 실패했습니다." >&2
        return 1
    fi

    case "${1:---list}" in
        --list)
            printf '%s\n' "$scanned" | awk -F'\t' '{ printf "%-10s %-24s %s\n", $1, $2, $4 }'
            ;;
        --write)
            write_schema_section "$scanned" || status=1
            [[ "$status" -eq 0 ]] && echo "생성 구간 갱신: $ledger_doc"
            ;;
        --check)
            check_headers "$scanned" || status=1
            # 헤더가 깨진 상태에서는 대장 비교 결과가 의미가 없으므로 건너뛴다.
            [[ "$status" -eq 0 ]] && { check_ledger_current "$scanned" || status=1; }
            check_config_declarations "$required_config" || status=1
            [[ "$status" -eq 0 ]] && echo "prod 스키마 헤더·대장과 필수 설정 선언 OK"
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

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
