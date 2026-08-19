-- prod-schema: mirror
-- 레거시 momens-api 000001_init.sql 의 milestones 테이블과 000006_fe_contract.sql 이 더한
-- 컬럼·milestone_owners 를 local/test 미러에 맞춥니다. 모바일이 마일스톤을 읽지 않아
-- 지금까지 이 서버에 없던 테이블입니다.
--
-- 웹 snapshot(H023)의 milestones 구획이 이 값들을 그대로 반환합니다. 계산값이 아니라 레거시가
-- 저장한 값을 읽기만 하므로 파생 정책이 없습니다(MOM-0858).
--
-- projects 와 달리 workspace_id 도 label 도 metadata 도 없습니다. 워크스페이스는 project 를
-- 거쳐서만 알 수 있어 조회가 projects 를 조인합니다.
CREATE TABLE milestones (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    target_date DATE,
    status TEXT NOT NULL DEFAULT 'planned'
        CHECK (status IN ('planned', 'active', 'completed', 'missed')),
    health_status TEXT NOT NULL DEFAULT 'planned'
        CHECK (health_status IN ('on_track', 'at_risk', 'blocked', 'planned', 'open')),
    progress INTEGER NOT NULL DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
    summary TEXT,
    last_context_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_milestones_project_id ON milestones(project_id) WHERE deleted_at IS NULL;

-- owner_user_ids 의 backing 입니다. 레거시는 조회 시 created_at, owner_user_id 순으로 집계하고
-- 행이 없으면 빈 배열로 둡니다. projects 와 달리 폴백할 owner_id 컬럼이 없습니다.
-- 정렬 컬럼이 created_at 뿐이라 updated_at 은 두지 않습니다(레거시 원본과 동일).
CREATE TABLE milestone_owners (
    milestone_id UUID NOT NULL REFERENCES milestones(id) ON DELETE CASCADE,
    owner_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (milestone_id, owner_user_id)
);
