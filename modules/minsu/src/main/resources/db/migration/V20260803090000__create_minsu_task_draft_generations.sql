-- prod-schema: required MOM-0840
-- Minsu task draft 비동기 생성 원장(MOM-0817, docs/design/minsu-async-task-draft-design.md 6절).
--
-- convert 트랜잭션이 pending 행을 적재하고 scheduler가 claim해 처리한다. tasks에는 생성이 성공한
-- 시점에만 반영하므로, 어떤 이유로 끝나든 tasks는 convert 시점의 고정 fallback draft를 유지한다.
--
-- 이 서버가 소유하는 신규 테이블이며 prod 반영은 레거시 마이그레이션(MOM-0825)이 담당한다
-- (docs/rules/persistence.md). 외부 테이블(tasks/workspaces) FK는 signal_actions·push_deliveries와
-- 같은 이유로 생략한다.
--
-- 배포 순서 제약: prod는 Flyway가 꺼져 있지만 ddl-auto=validate는 적용되므로, 이 엔티티가 prod로
-- 나가기 전에 MOM-0825가 먼저 반영돼야 한다. 순서가 뒤집히면 테이블이 없어 부팅이 실패한다.
-- 설정 3축이 모두 기본 비활성인 것과 무관한 제약이다.
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
    -- 보존 정책이 닫으며 그 정책은 MOM-0825에서 확정한다. 단 아래 NOT NULL 때문에 그 정책은 NULL로
    -- 비우는 형태일 수 없다. 빈 값('' / '[]') 덮어쓰기나 행 삭제여야 하고, NULL 방식을 고르면
    -- 운영 데이터에 DROP NOT NULL이 필요해진다.
    -- title·type·description은 signals에서 NOT NULL이고 원장은 그 입력을 그대로 보존하므로 여기서도
    -- NOT NULL이다. impact만 원본 계약대로 nullable이다.
    signal_title TEXT NOT NULL,
    signal_type TEXT NOT NULL,
    signal_description TEXT NOT NULL,
    signal_impact TEXT,
    signal_evidence JSONB NOT NULL,

    -- 반영 baseline(6절). convert가 tasks에 실제로 쓴 값을 복사해 둔다. 반영 시점에 고정 fallback
    -- 규칙을 다시 계산해 비교하지 않는다. 재계산 방식은 규칙이 바뀌는 순간 진행 중이던 작업 전부가
    -- CAS 불일치가 되어 편집이 없는데도 user_edited로 오분류된다.
    -- baseline은 worker 산출물인 snapshot과 달리 서버가 tasks에 직접 쓴 값이다.
    --
    -- role이 NOT NULL인 이유는 반영 CAS가 동등 비교이기 때문이다(8.1절). baseline_role이 NULL이면
    -- `role = NULL`이 항상 거짓이라 사용자가 편집하지 않았는데도 user_edited로 오분류된다.
    -- tasks.role이 nullable인 것(V20260715090000)은 웹 생성 태스크 사정이고 convert 경로와 무관하다.
    -- 비동기 적재 시 baseline은 convert가 쓴 고정 fallback이므로 오늘 값은 'pm'·'medium'뿐이다
    -- (DefaultSignalTaskDraftGenerator.fallback).
    --
    -- 아래 CHECK는 지금 막을 대상이 없다. 값이 고정이기 때문이다. 나중에 baseline이 다양해질 때
    -- tasks 계약(V20260706120000, V20260707150000) 밖의 값이 들어오는 것을 막으려고 둔다. 오염되면
    -- 반영 CAS가 예외 없이 무매칭으로 넘어가 조용히 user_edited가 된다.
    --
    -- 기준은 생산자인 minsu.Role·minsu.Priority가 아니라 tasks 계약이다. baseline은 정의상 convert가
    -- tasks에 쓴 값이라 유효 도메인이 tasks의 도메인이고, CAS 비교 상대도 tasks의 컬럼이다. 생산자
    -- 기준으로 좁히면(예: minsu.Priority에 없는 'urgent' 제외) tasks는 받는데 원장만 거부하는 구간이
    -- 생겨 이 CHECK가 단독 실패 지점이 된다. 적재는 convert 트랜잭션 안이므로 그 실패는 사용자
    -- convert 요청 실패로 나타난다. tasks 기준이면 같은 트랜잭션의 tasks 쓰기가 먼저 막히므로 이
    -- CHECK가 최초 실패 지점이 되는 경로가 없고, 복사본 오염에 대한 이중 방어로만 남는다.
    baseline_title TEXT NOT NULL,
    baseline_role TEXT NOT NULL CHECK (baseline_role IN ('pm', 'design', 'backend', 'frontend')),
    baseline_priority TEXT NOT NULL
        CHECK (baseline_priority IN ('low', 'medium', 'high', 'urgent')),

    -- 읽기 투영이 generating을 닫는 시각과 tasks 반영을 포기하는 시각(8.6절). 두 값은 적재 시점에
    -- 계산해 저장한다. 조회할 때마다 현재 설정으로 다시 계산하면 설정 변경이 과거 작업의 상태를 뒤집는다.
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
    -- lease를 함께 정리한다는 7.1절 규칙을 제약으로 강제한다. 두 필드를 각각 상태와 묶어야 한다.
    -- 한 조건에 AND로 묶으면 (pending, token 있음, lease 없음)처럼 한쪽만 남은 행이 양변 false로
    -- 통과한다.
    CONSTRAINT minsu_task_draft_generations_claim_check
        CHECK ((status = 'processing') = (claim_token IS NOT NULL)
           AND (status = 'processing') = (lease_expires_at IS NOT NULL)),

    -- 가드 밴드(8.6절). apply_cutoff_at = 적재 + 상한 - margin, read_deadline_at = 적재 + 상한이므로
    -- 순서가 고정된다. margin이 0이면 반영 CAS의 조건 평가와 읽기 투영이 같은 경계에서 갈려, 앱이
    -- ready + fallback title을 받은 뒤 title이 바뀌는 인터리빙이 열린다. margin 값 자체는 후속
    -- 티켓에서 정하지만 margin > 0은 값과 무관하게 성립해야 한다.
    CONSTRAINT minsu_task_draft_generations_deadline_check
        CHECK (apply_cutoff_at < read_deadline_at)
);

-- scheduler가 재시도 시각이 지난 pending 행을 주기 스캔한다.
CREATE INDEX idx_minsu_task_draft_generations_pending_next_attempt
    ON minsu_task_draft_generations (next_attempt_at) WHERE status = 'pending';

-- lease가 만료된 processing 행을 회수한다(8.5절).
CREATE INDEX idx_minsu_task_draft_generations_expired_lease
    ON minsu_task_draft_generations (lease_expires_at) WHERE status = 'processing';
