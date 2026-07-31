-- local/test 전용 signals 미러.
--
-- signals는 worker가 생성하는 Signal 원본이다(ADR-0007). 서버는 signals를 쓰지 않고 읽기만 한다
-- (Signal 엔티티는 @Immutable). 운영(prod) 공유 DB의 signals는 worker/레거시 마이그레이션이 소유하며
-- (prod 스키마 반영 위치는 MOM-74에서 확정), 서버는 Flyway OFF·ddl-auto=validate로 매핑만 검증한다
-- ([데이터] docs/rules/persistence.md).
--
-- 이 마이그레이션은 local/test(별도 DB)에서 Signal 읽기 엔티티 매핑 검증과 fixture 삽입을 위해서만 실행된다.
-- source_refs 미러와 같은 방식으로, 외부(worker)가 쓰는 테이블이라 workspaces/projects FK는 미러에서 생략한다.
CREATE TABLE signals (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('decision', 'risk', 'question', 'change')),
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    impact TEXT,
    minsu_suggestion TEXT,
    metadata JSONB,
    occurred_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

-- 목록은 프로젝트 스코프 + 미삭제 + 생성 시각 내림차순(동률 시 id 내림차순)으로 조회한다.
CREATE INDEX idx_signals_project_live ON signals(project_id, created_at DESC) WHERE deleted_at IS NULL;
