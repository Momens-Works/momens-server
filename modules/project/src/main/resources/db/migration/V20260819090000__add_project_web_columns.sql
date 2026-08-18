-- prod-schema: mirror
-- 레거시 momens-api 000006_fe_contract.sql·000008_project_metadata.sql·000017_project_label.sql 이
-- projects 에 추가한 컬럼과 project_owners 테이블을 local/test 미러에 맞춥니다.
-- V20260703150000__create_project.sql 이 모바일 read 범위까지만 만들면서 남겨둔 항목입니다.
--
-- 웹 snapshot(H023)의 projects 구획이 이 값들을 그대로 반환합니다. 계산값이 아니라 레거시가
-- 저장한 값을 읽기만 하므로 파생 정책이 없습니다(MOM-0857).
--
-- progress 는 이미 create_project 에 있고 이 서버는 매핑하지 않습니다(ADR-0013).
ALTER TABLE projects
    ADD COLUMN label TEXT,
    ADD COLUMN health_status TEXT NOT NULL DEFAULT 'open'
        CHECK (health_status IN ('on_track', 'at_risk', 'blocked', 'planned', 'open')),
    ADD COLUMN unresolved_count INTEGER NOT NULL DEFAULT 0 CHECK (unresolved_count >= 0),
    ADD COLUMN voc_signal_count INTEGER NOT NULL DEFAULT 0 CHECK (voc_signal_count >= 0),
    ADD COLUMN last_context_at TIMESTAMPTZ,
    ADD COLUMN metadata JSONB;

-- 레거시 000017 은 label 컬럼과 함께 PRJ 접두사(workspace_label_sequences CHECK)와 자동 발급
-- 트리거(trg_projects_label)를 추가했습니다. 이 서버는 project write 경로가 없어 컬럼만 미러하고,
-- 발급 경로는 MOM-0866(프로젝트·마일스톤 write 이관)이 소유합니다.

-- owner_user_ids 의 backing 입니다. 레거시는 조회 시 created_at, owner_user_id 순으로 집계하고
-- 행이 없으면 projects.owner_id 로 폴백합니다. 정렬 컬럼이 created_at 뿐이라 updated_at 은 두지
-- 않습니다(레거시 원본과 동일).
CREATE TABLE project_owners (
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    owner_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (project_id, owner_user_id)
);
