#!/usr/bin/env bash
#
# 부트스트랩 분류 누락 검사 (MOM-0909).
#
# 리포의 모든 마이그레이션이 부트스트랩에서 **심기와 실행 중 하나로 명시 분류**됐는지 검사한다.
# 어느 목록에도 없으면 실패한다.
#
# 왜 필요한가. 심기 목록에 넣어야 할 파일을 빠뜨리면 그 파일이 prod 에서 실행되고, 레거시가 이미
# 만든 객체라면 `already exists` 로 죽는다. 그 실패가 지금은 **prod 기동 실패로만 드러난다.**
# 실제로 한 번 일어났다 — `V20260823100000__add_source_ref_content_hash.sql` 이 목록 없이 머지됐다.
#
# 판정은 사람이 한다. 이 검사는 판정을 유도하지 않고 **판정이 적혀 있는지만** 본다.
#
# 헤더(`-- prod-schema:`)를 근거로 쓰지 않는다. MOM-0910 이 그 체계를 폐지했고 새 마이그레이션에는
# 헤더가 없다.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
seed_manifest="$repo_root/scripts/prod-flyway-bootstrap-seed.txt"
exec_manifest="$repo_root/scripts/prod-flyway-bootstrap-exec.txt"

versions_in() {
    grep -oE '^[0-9]+' "$1" | sort -u
}

repo_versions() {
    find "$repo_root" -type d \( -name build -o -name .git -o -name .gradle-work \) -prune -o \
        -path '*/src/main/resources/db/migration/V*.sql' -print \
        | sed -E 's|.*/V([0-9]+)__.*|\1|' \
        | sort -u
}

main() {
    local classified unclassified orphan status=0

    for file in "$seed_manifest" "$exec_manifest"; do
        [[ -f "$file" ]] || { echo "::error::분류 목록이 없습니다: $file" >&2; return 1; }
    done

    classified="$(cat <(versions_in "$seed_manifest") <(versions_in "$exec_manifest") | sort -u)"

    # 양쪽에 동시에 있으면 판정이 모순이다.
    local duplicate
    duplicate="$(comm -12 <(versions_in "$seed_manifest") <(versions_in "$exec_manifest"))"
    if [[ -n "$duplicate" ]]; then
        status=1
        echo "::error::심기와 실행 양쪽에 있는 마이그레이션이 있습니다."
        printf '%s\n' "$duplicate" | sed 's/^/  /'
    fi

    unclassified="$(comm -23 <(repo_versions) <(printf '%s\n' "$classified"))"
    if [[ -n "$unclassified" ]]; then
        status=1
        echo "::error::부트스트랩 분류가 없는 마이그레이션이 있습니다."
        printf '%s\n' "$unclassified" | sed 's/^/  /'
        cat <<'USAGE'

  이 파일이 만드는 객체가 prod 에 이미 있는지 대조하고, 결과에 따라 둘 중 하나에 추가하세요.
    scripts/prod-flyway-bootstrap-seed.txt   prod 에 이미 있어 실행하면 실패한다 (심기)
    scripts/prod-flyway-bootstrap-exec.txt   prod 에 없다. 부트스트랩에서 실행한다

  판정 기준은 docs/design/prod-schema-ownership-transfer.md 4절입니다.
  레거시가 이미 만든 객체를 local/test 에 재현하는 파일이면 거의 항상 심기입니다.
USAGE
    fi

    orphan="$(comm -13 <(repo_versions) <(printf '%s\n' "$classified"))"
    if [[ -n "$orphan" ]]; then
        status=1
        echo "::error::분류 목록에 있으나 마이그레이션 파일이 없습니다."
        printf '%s\n' "$orphan" | sed 's/^/  /'
    fi

    [[ "$status" -eq 0 ]] && echo "부트스트랩 분류 OK ($(printf '%s\n' "$classified" | grep -c .)건)"
    return "$status"
}

main "$@"
