-- prod-schema: mirror
-- 레거시 momens-api 000003_memory.sql의 write 대상 review_actions 미러입니다.
-- 레거시와 같은 컬럼·제약을 유지하며, 읽기 API는 이 티켓 범위에 포함하지 않습니다.
CREATE TABLE review_actions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    action_type TEXT NOT NULL,
    reviewer_user_id UUID NOT NULL,
    edited_title TEXT,
    edited_summary TEXT,
    edited_body TEXT,
    rejection_reason TEXT,
    merge_target_memory_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_review_actions_candidate_id ON review_actions(candidate_id);

CREATE UNIQUE INDEX idx_memory_candidates_workspace_label
    ON memory_candidates(workspace_id, label)
    WHERE label IS NOT NULL;
CREATE UNIQUE INDEX idx_confirmed_memories_workspace_label
    ON confirmed_memories(workspace_id, label)
    WHERE label IS NOT NULL;
CREATE INDEX idx_confirmed_memories_candidate ON confirmed_memories(created_from_candidate_id);
