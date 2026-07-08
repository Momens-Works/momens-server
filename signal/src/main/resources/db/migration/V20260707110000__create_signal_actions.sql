-- local/test 전용 signal_actions 미러.
--
-- signal_actions는 api-server가 사용자 action(convert-to-task/dismiss)을 기록하는 원장이다(ADR-0008).
-- MOM-64 목록은 이 테이블의 존재만 읽어 "아직 처리되지 않은 Signal"을 거른다(NOT EXISTS). 쓰기 로직과
-- 엔티티는 MOM-65에서 붙인다. unique(signal_id)는 Signal 하나당 action 하나라는 원장 불변식이다.
--
-- signals 미러와 같은 방식으로 local/test(별도 DB)에서만 만들고, prod 스키마 반영 위치는 MOM-74에서 확정한다.
-- 외부 테이블(workspaces/users/signals) FK는 미러에서 생략한다.
CREATE TABLE signal_actions (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    signal_id UUID NOT NULL,
    action_type TEXT NOT NULL CHECK (action_type IN ('convert_to_task', 'dismiss')),
    result_task_id UUID,
    processed_by_user_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (signal_id)
);
