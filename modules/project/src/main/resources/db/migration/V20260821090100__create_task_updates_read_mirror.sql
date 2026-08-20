-- prod-schema: mirror
-- 레거시 momens-api 000012_task_updates.sql의 읽기 전용 미러입니다.
CREATE TABLE task_updates (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_id UUID,
    body TEXT NOT NULL,
    kind TEXT NOT NULL DEFAULT 'comment' CHECK (kind IN ('comment', 'update')),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_task_updates_task_id ON task_updates(task_id, created_at)
    WHERE deleted_at IS NULL;
