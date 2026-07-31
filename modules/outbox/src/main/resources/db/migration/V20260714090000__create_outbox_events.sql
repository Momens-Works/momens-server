-- local/test 전용 outbox_events 미러(SD-2, ADR-0008).
--
-- append-only 발행 로그다. 이벤트를 발생시킨 쪽(worker 또는 api-server)이 자기 트랜잭션에서 INSERT하고,
-- consumer는 자체적으로 소비 상태를 관리한다(status/processed 컬럼 없음). id는 bigserial로 consumer의
-- polling watermark 역할을 한다. idempotency_key = "{event_type}:{aggregate_id}"는 UNIQUE라
-- ON CONFLICT DO NOTHING으로 결정적 dedup을 건다(SD-3).
--
-- prod 스키마 반영 위치는 CO-8에 따라 레거시 momens-api 마이그레이션으로 별도 관리한다. 외부 테이블
-- (workspaces) FK는 다른 미러와 같은 이유로 생략한다.
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    issued_by TEXT NOT NULL CHECK (issued_by IN ('worker', 'api-server')),
    workspace_id UUID NOT NULL,
    aggregate_type TEXT NOT NULL,
    aggregate_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_events_workspace_id ON outbox_events(workspace_id);
