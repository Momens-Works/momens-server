-- prod-schema: mirror
-- 레거시 momens-api 000001_init.sql 의 blockers 테이블을 local/test에 미러합니다.
-- 웹 snapshot(H023)의 blockers 구획이 이 값을 그대로 읽으며, 이 서버의 write 경로는
-- MOM-0859 범위에 포함하지 않습니다.
--
-- blockers에는 soft-delete 컬럼이 없습니다. resolve는 status와 resolved_at을 갱신하고,
-- 삭제는 물리 삭제하므로 목록은 workspace_id만 필터링합니다.
CREATE TABLE blockers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'resolved')),
    blocked_entity_type TEXT NOT NULL CHECK (blocked_entity_type IN ('task', 'milestone')),
    blocked_entity_id UUID NOT NULL,
    task_id UUID REFERENCES tasks(id) ON DELETE CASCADE,
    milestone_id UUID REFERENCES milestones(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    CHECK (
        (blocked_entity_type = 'task' AND task_id = blocked_entity_id AND milestone_id IS NULL)
        OR
        (blocked_entity_type = 'milestone' AND milestone_id = blocked_entity_id AND task_id IS NULL)
    )
);
