-- local/test 전용 outbox_events 미러.
--
-- outbox_events는 도메인 write 트랜잭션 안에서 append되는 append-only 발행 로그다(ADR-0008, SD-2). 이벤트를
-- 발생시킨 주체(issued_by)가 자기 트랜잭션에서 INSERT하며, api-server(사용자 액션·task 생성)와 worker
-- (signal.created)가 같은 envelope를 공유한다. status/updated_at 컬럼은 없다 — consumer가 소비 상태를 자체
-- 관리한다(재시도·DLQ는 worker, indexing은 retrieval).
--
-- idempotency_key("{event_type}:{aggregate_id}")의 UNIQUE 제약과 INSERT ... ON CONFLICT DO NOTHING이 중복
-- 이벤트를 막는 두 번째 층이다(SD-3, ledger UNIQUE(signal_id)가 첫 층).
--
-- signal_actions 미러와 같은 방식으로 local/test(별도 DB)에서만 만들고, prod 스키마 반영 위치는 레거시
-- 마이그레이션에서 확정한다. 외부 테이블(workspaces) FK는 미러에서 생략한다.
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    issued_by TEXT NOT NULL CHECK (issued_by IN ('worker', 'api-server')),
    workspace_id UUID NOT NULL,
    aggregate_type TEXT,
    aggregate_id TEXT,
    event_type TEXT,
    payload JSONB,
    idempotency_key TEXT UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
