-- prod-schema: mirror
-- 레거시 momens-api 000006_fe_contract.sql:186-192 의 라벨 UNIQUE 인덱스 미러입니다.
--
-- V20260821140000 이 이 제약을 두지 않은 것은 레거시에 없다고 본 오판이었습니다(MOM-0869 리뷰).
-- 그 파일은 이미 머지돼 checksum 이 고정이라 고치지 않고 여기서 되돌립니다.
--
-- 부분 UNIQUE 인덱스지만 미러 기준에서는 인덱스가 아니라 제약입니다. 이 서버가 두 테이블에 쓰기
-- 시작했으므로(MOM-0869) 레거시와 똑같아야 합니다. 미러가 더 느슨하면 prod 가 거부할 중복 라벨을
-- local/test 가 통과시킵니다(docs/rules/persistence.md).
--
-- IF NOT EXISTS 인 이유는 dev·prod 가 레거시와 같은 DB 를 쓰고 같은 이름의 인덱스가 거기 이미 있기
-- 때문입니다. 조건 없이 만들면 실배포 Flyway 가 relation already exists 로 죽습니다(MOM-0795).
-- 부분 조건까지 레거시와 같게 둡니다. workspace_id 가 NOT NULL 이고 Postgres 기본이 NULLS DISTINCT
-- 라, 이 데이터에서는 조건을 빼도 거부하는 집합이 같습니다. 그래도 맞추는 이유는 미러의 기준이
-- "레거시와 같은가"이지 "결과가 같아 보이는가"가 아니기 때문입니다. 레거시가 조건을 바꾸거나
-- NULLS NOT DISTINCT 가 끼어들면 그때 갈리는데, 그 시점에는 이미 갈린 뒤입니다.
CREATE UNIQUE INDEX IF NOT EXISTS idx_memory_candidates_workspace_label
    ON memory_candidates(workspace_id, label)
    WHERE label IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_confirmed_memories_workspace_label
    ON confirmed_memories(workspace_id, label)
    WHERE label IS NOT NULL;
