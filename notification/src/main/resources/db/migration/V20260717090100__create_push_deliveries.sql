-- signal.created notification consumer 상태와 기기별 발송 상태(MOM-0690,
-- docs/design/signal-push-demo-design.md 10절). push_installations와 같이 이 서버가 소유하는
-- 신규 상태이며 prod 스키마 반영 경로는 범위 밖이다. 외부 테이블 FK는 같은 이유로 생략한다.

-- consumer의 outbox polling watermark. consumer_name 행을 잠그고 전진시켜 여러 인스턴스가
-- 같은 outbox 구간을 동시에 materialize하지 않게 한다. 상태 행이라 감사 시각은 updated_at만 쓴다.
CREATE TABLE notification_consumer_offsets (
    consumer_name TEXT PRIMARY KEY,
    last_outbox_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 기기별 발송 상태. (outbox_event_id, installation_id) 복합 PK가 동일 event의 기기별 중복
-- materialization을 막는다(별도 surrogate 없음). token은 복제하지 않고 installation을 참조하며,
-- 전송 직전 installation의 활성 여부와 소유 사용자를 다시 확인한다(10.2절).
CREATE TABLE push_deliveries (
    outbox_event_id BIGINT NOT NULL,
    installation_id UUID NOT NULL,
    target_user_id UUID NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('pending', 'sent', 'failed', 'cancelled')),
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claim_token UUID,
    failure_category TEXT,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (outbox_event_id, installation_id)
);

-- 발송기가 재시도 시각이 지난 pending 행을 주기 스캔한다(10.3절).
CREATE INDEX idx_push_deliveries_pending_next_attempt
    ON push_deliveries (next_attempt_at) WHERE status = 'pending';
