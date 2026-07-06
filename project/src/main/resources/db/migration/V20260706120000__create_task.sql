-- 레거시 momens-api 000001_init.sql / 000006_fe_contract.sql 의 tasks 테이블과 호환됩니다.
-- 모바일 보드/생성(MOM-62)이 읽고 쓰는 범위까지만 만듭니다. 제외한 레거시 컬럼은 태스크
-- 상세/수정(MOM-63)과 웹 이관에서 별도 마이그레이션으로 추가합니다:
--   milestone_id, description, assignee_id, due_date.
-- roles 는 레거시 tasks 에 없는 신규 속성이라, 기존 tasks 를 바꾸지 않고 부가 테이블 task_roles 로 둡니다.
-- (local/test 전용 Flyway. prod 공유 스키마의 task_roles 는 컷오버 시 레거시 마이그레이션으로 추가합니다.)
CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    label TEXT,
    title TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'backlog'
        CHECK (status IN ('backlog', 'todo', 'in_progress', 'done', 'cancelled')),
    priority TEXT NOT NULL DEFAULT 'medium'
        CHECK (priority IN ('low', 'medium', 'high', 'urgent')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_tasks_project_id ON tasks(project_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX idx_tasks_workspace_label ON tasks(workspace_id, label)
    WHERE workspace_id IS NOT NULL AND label IS NOT NULL;

CREATE TABLE task_roles (
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('pm', 'design', 'backend', 'frontend', 'android', 'qa')),
    PRIMARY KEY (task_id, role)
);
