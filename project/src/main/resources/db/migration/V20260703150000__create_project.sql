-- 레거시 momens-api 000001_init.sql 의 projects 테이블과 호환됩니다.
-- 모바일 read 기반(MOM-59)이 읽는 범위까지만 만듭니다. 000006_fe_contract.sql 컬럼 중
-- 모바일 project 스냅샷이 쓰는 target_date, progress, summary 만 포함합니다.
-- 제외한 레거시 컬럼과 테이블은 웹 이관(MOM-35 계열)에서 별도 마이그레이션으로 추가합니다:
--   health_status, unresolved_count, voc_signal_count, last_context_at (000006),
--   metadata (000008), label (000017), project_owners 테이블 (000006).
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived')),
    owner_id UUID NOT NULL REFERENCES users(id),
    target_date DATE,
    progress INTEGER NOT NULL DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
    summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_projects_workspace_id ON projects(workspace_id) WHERE deleted_at IS NULL;
