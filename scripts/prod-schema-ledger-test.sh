#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$repo_root/scripts/prod-schema-ledger.sh"

fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT

mkdir -p "$fixture_root/app/src/main/resources" "$fixture_root/docs"

cat > "$fixture_root/app/src/main/resources/application.yml" <<'YAML'
plain: ${REAL_REQUIRED}
commented: value # ${COMMENT_ONLY}
double-quoted: "value # ${DOUBLE_QUOTED_REQUIRED}"
single-quoted: 'value # ${SINGLE_QUOTED_REQUIRED}'
fragment: https://example.com/#${URL_FRAGMENT_REQUIRED}
YAML
: > "$fixture_root/app/src/main/resources/application-prod.yml"

repo_root="$fixture_root"
prod_config_files=(
    "app/src/main/resources/application.yml"
    "app/src/main/resources/application-prod.yml"
)

expected_config="$(printf '%s\n' \
    DOUBLE_QUOTED_REQUIRED REAL_REQUIRED SINGLE_QUOTED_REQUIRED URL_FRAGMENT_REQUIRED | sort)"
actual_config="$(scan_prod_required_config)"
if [[ "$actual_config" != "$expected_config" ]]; then
    echo "prod 필수 설정 주석 처리 실패" >&2
    diff -u <(printf '%s\n' "$expected_config") <(printf '%s\n' "$actual_config") || true
    exit 1
fi

ledger_doc="docs/ledger.md"

write_config_declarations() {
    {
        printf '%s\n' "$config_begin"
        printf '%s\n' '| 환경변수 | 주입 위치 | prod 상태 |'
        printf '%s\n' '| --- | --- | --- |'
        printf '%s\n' "$@"
        printf '%s\n' "$config_end"
    } > "$repo_root/$ledger_doc"
}

expect_config_declaration_failure() {
    local label="$1" required="$2"
    shift 2
    write_config_declarations "$@"
    if check_config_declarations "$required" >/dev/null 2>&1; then
        echo "prod 필수 설정 선언의 $label 검증을 통과했습니다." >&2
        exit 1
    fi
}

expect_config_declaration_failure "누락" $'ALPHA_REQUIRED\nBETA_REQUIRED' \
    '| `ALPHA_REQUIRED` | `secret` | `applied` |'
expect_config_declaration_failure "잉여" 'ALPHA_REQUIRED' \
    '| `ALPHA_REQUIRED` | `secret` | `applied` |' \
    '| `BETA_REQUIRED` | `configmap` | `required` |'
expect_config_declaration_failure "중복" 'ALPHA_REQUIRED' \
    '| `ALPHA_REQUIRED` | `secret` | `applied` |' \
    '| `ALPHA_REQUIRED` | `secret` | `applied` |'

cat > "$repo_root/$ledger_doc" <<EOF
# fixture
$schema_end
manual
$schema_begin
tail
EOF
if check_marker_pair "$schema_begin" "$schema_end" "test" >/dev/null 2>&1; then
    echo "역순 마커를 허용했습니다." >&2
    exit 1
fi

cat > "$repo_root/$ledger_doc" <<EOF
# fixture
$schema_begin
old
$schema_end
manual
EOF
chmod 0644 "$repo_root/$ledger_doc"
render_schema_section() {
    printf '%s\n' "new"
}
write_schema_section "unused"

expected_ledger="$(cat <<EOF
# fixture
$schema_begin
new
$schema_end
manual
EOF
)"
actual_ledger="$(cat "$repo_root/$ledger_doc")"
if [[ "$actual_ledger" != "$expected_ledger" ]]; then
    echo "스키마 생성 구간 교체 실패" >&2
    exit 1
fi

case "$(uname -s)" in
    Darwin) ledger_mode="$(stat -f '%Lp' "$repo_root/$ledger_doc")" ;;
    *) ledger_mode="$(stat -c '%a' "$repo_root/$ledger_doc")" ;;
esac
if [[ "$ledger_mode" != "644" ]]; then
    echo "대장 파일 권한이 바뀌었습니다: $ledger_mode" >&2
    exit 1
fi

echo "prod-schema-ledger 회귀 테스트 OK"
