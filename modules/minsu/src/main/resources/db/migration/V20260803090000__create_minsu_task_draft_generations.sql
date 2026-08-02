-- Minsu task draft 비동기 생성 원장(MOM-0817, docs/design/minsu-async-task-draft-design.md 6절).
--
-- convert 트랜잭션이 pending 행을 적재하고 scheduler가 claim해 처리한다. tasks에는 생성이 성공한
-- 시점에만 반영하므로, 어떤 이유로 끝나든 tasks는 convert 시점의 고정 fallback draft를 유지한다.
--
-- 이 서버가 소유하는 신규 테이블이며 prod 반영은 레거시 마이그레이션(MOM-0825)이 담당한다
-- (docs/rules/persistence.md). 외부 테이블(tasks/workspaces) FK는 signal_actions·push_deliveries와
-- 같은 이유로 생략한다.
--
-- 테이블명을 minsu_generations 같은 총칭이 아니라 task draft로 좁힌 것은 의도적이다. 컬럼 절반이
-- task draft 전용(반영 baseline, read_deadline_at)이라 다른 종류의 생성은 자기 테이블을 갖는 편이
-- 자연스럽고, 그때 총칭 이름이 비어 있어야 한다.
CREATE TABLE minsu_task_draft_generations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    task_id UUID NOT NULL,

    -- convert 시점 SignalTaskDraftInput snapshot(5.6절). 실행 시점 재조회(hydrate)를 하지 않으므로
    -- 사용자가 convert를 누른 시점의 근거가 그대로 보존된다. description·impact에 자체 상한을 두지
    -- 않는다: 상한을 두면 저장본이 convert 시점 입력과 달라지고, 길이 계약의 주체는 생산자인
    -- worker다(signal_evidence 30자 계약과 같은 이유). 복제량은 terminal 이후 snapshot을 비우는
    -- 보존 정책이 닫으며 그 정책은 MOM-0825에서 확정한다.
    signal_title TEXT,
    signal_type TEXT,
    signal_description TEXT,
    signal_impact TEXT,
    signal_evidence JSONB NOT NULL,

    -- 반영 baseline(6절). convert가 tasks에 실제로 쓴 값을 복사해 둔다. 반영 시점에 고정 fallback
    -- 규칙을 다시 계산해 비교하지 않는다. 재계산 방식은 규칙이 바뀌는 순간 진행 중이던 작업 전부가
    -- CAS 불일치가 되어 편집이 없는데도 user_edited로 오분류된다.
    baseline_title TEXT NOT NULL,
    baseline_role TEXT,
    baseline_priority TEXT NOT NULL,

    -- 읽기 투영이 generating을 닫는 시각과 tasks 반영을 포기하는 시각(8.6절).
    read_deadline_at TIMESTAMPTZ NOT NULL,
    apply_cutoff_at TIMESTAMPTZ NOT NULL,

    status TEXT NOT NULL CHECK (status IN ('pending', 'processing', 'completed')),
    completion_reason TEXT CHECK (completion_reason IN (
        'generated', 'user_edited', 'task_gone', 'operationally_closed',
        'deadline_exceeded', 'insufficient_context', 'invalid_config', 'retry_exhausted')),
    attempt_count INT NOT NULL DEFAULT 0,

    -- lease와 next_attempt_at은 별도 컬럼이다(7.1절). 전자는 실행 중 소유권 만료, 후자는 대기 중
    -- 재시도 시각으로 의미가 다르다. 한 컬럼으로 겸하면 lease 만료 회수와 백오프 대기를 구분할 수 없다.
    claim_token UUID,
    lease_expires_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 멱등 키(8.4절). 상류의 signal_actions UNIQUE(signal_id)가 1차 방어이고 이것이 이중 방어다.
    -- task 하나당 draft 생성 하나. 두 번째 종류의 생성이 생기면 자기 테이블을 갖는다.
    UNIQUE (task_id),

    -- 종료 사유는 상태가 아니라 별도 컬럼이므로(7.1절) 둘의 정합을 DB가 지킨다.
    CONSTRAINT minsu_task_draft_generations_reason_check
        CHECK ((status = 'completed') = (completion_reason IS NOT NULL)),
    -- claim 보유는 processing에서만 성립한다. retryable 실패로 pending에 되돌릴 때 이전 token과
    -- lease를 함께 정리한다는 7.1절 규칙을 제약으로 강제한다.
    CONSTRAINT minsu_task_draft_generations_claim_check
        CHECK ((status = 'processing') = (claim_token IS NOT NULL AND lease_expires_at IS NOT NULL))
);

-- scheduler가 재시도 시각이 지난 pending 행을 주기 스캔한다.
CREATE INDEX idx_minsu_task_draft_generations_pending_next_attempt
    ON minsu_task_draft_generations (next_attempt_at) WHERE status = 'pending';

-- lease가 만료된 processing 행을 회수한다(8.5절).
CREATE INDEX idx_minsu_task_draft_generations_expired_lease
    ON minsu_task_draft_generations (lease_expires_at) WHERE status = 'processing';
