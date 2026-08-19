-- prod-schema: mirror
-- 레거시 momens-api 000003_memory.sql 의 memory_candidates·confirmed_memories 와
-- 000006_fe_contract.sql 이 더한 label·expires_at 을 local/test 미러에 맞춥니다. 모바일이 후보·메모리를
-- 읽지 않아 지금까지 이 서버에 없던 테이블입니다.
--
-- 웹 snapshot(H023)의 memory_candidates·memories 구획이 이 값들을 그대로 반환합니다. 계산값이 아니라
-- 레거시가 저장한 값을 읽기만 하므로 파생 정책이 없습니다(MOM-0860).
--
-- snapshot 응답 키는 memories 지만 레거시 테이블 이름은 confirmed_memories 입니다. 이름을 맞추지 않고
-- 레거시 스키마를 따릅니다. prod 는 공유 DB 라 테이블 이름을 고를 여지가 없습니다.
--
-- 세 번째 테이블 review_actions 는 만들지 않습니다. 레거시에서도 INSERT 만 있고 SELECT 가 한 곳도
-- 없는 감사 로그라(memory/repository.go:94 가 유일한 접점) read 기반에 필요하지 않습니다. 후보 리뷰
-- write 를 이관하는 MOM-0869 이 엔티티와 함께 만듭니다.
--
-- 레거시의 label 자동 발급 트리거(trg_memory_candidates_label, trg_confirmed_memories_label)와
-- next_workspace_label() 함수도 두지 않습니다. 이 서버는 트리거 대신 workspace 모듈의 LabelAllocator
-- public API 로 명시 발급하기로 이미 정했고(V20260627100000__create_workspace_label_sequences.sql),
-- 그 테이블의 label_prefix CHECK 가 SUG·MEM 을 미리 허용해 둔 것도 이 모듈을 예상한 것입니다.
-- 이번 범위에는 쓰기가 없어 발급 자체가 일어나지 않으므로 LabelAllocator 배선도 하지 않습니다.
--
-- 다른 모듈이 소유한 테이블로 나가는 FK(workspaces, users)는 두지 않습니다. 이 모듈은 런타임에 어떤
-- 기능 모듈에도 의존하지 않는데, 미러의 FK 하나 때문에 workspace·user 마이그레이션을 테스트
-- 클래스패스로 끌어오게 되기 때문입니다. prod 의 ddl-auto=validate 는 컬럼과 타입만 검증하고 FK 는
-- 보지 않으므로 재현해도 얻는 것이 없습니다(source·context 읽기 미러와 같은 판단). 모듈 안에서 닫히는
-- created_from_candidate_id 만 FK 로 남깁니다.
CREATE TABLE memory_candidates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    label TEXT,
    candidate_type TEXT NOT NULL,
    title TEXT NOT NULL,
    summary TEXT,
    body TEXT,
    confidence DOUBLE PRECISION,
    importance DOUBLE PRECISION,
    status TEXT NOT NULL DEFAULT 'PROPOSED'
        CHECK (status IN ('PROPOSED', 'CONFIRMED', 'REJECTED', 'MERGED', 'EXPIRED')),
    source_ref_ids UUID[],
    related_entity_ids UUID[],
    proposed_by TEXT NOT NULL DEFAULT 'CURATOR',
    reviewed_at TIMESTAMPTZ,
    reviewed_by_user_id UUID,
    rejection_reason TEXT,
    expires_at TIMESTAMPTZ,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 소프트 삭제 컬럼이 없습니다. 레거시가 후보를 지우지 않고 상태(REJECTED, EXPIRED)로만 다루기
-- 때문이며, 그래서 목록 조회에도 삭제 필터가 없습니다(웹 snapshot 계약 4.4).
CREATE TABLE confirmed_memories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    label TEXT,
    memory_type TEXT NOT NULL,
    title TEXT NOT NULL,
    summary TEXT,
    body TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INVALIDATED', 'ARCHIVED', 'DELETED')),
    source_ref_ids UUID[],
    related_entity_ids UUID[],
    created_from_candidate_id UUID REFERENCES memory_candidates(id),
    confirmed_by_user_id UUID,
    confirmed_at TIMESTAMPTZ,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    invalidated_by_user_id UUID,
    invalidation_reason TEXT,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

-- 이 모듈의 조회가 쓰는 인덱스만 둡니다. 레거시의 label unique 인덱스와
-- idx_confirmed_memories_candidate 는 쓰기 경로를 지키는 제약이라 write 이관(MOM-0869)에 맡깁니다.
CREATE INDEX idx_memory_candidates_workspace_status ON memory_candidates(workspace_id, status);
CREATE INDEX idx_confirmed_memories_workspace_id ON confirmed_memories(workspace_id) WHERE deleted_at IS NULL;
