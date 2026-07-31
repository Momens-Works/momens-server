-- local/test 전용 source_refs 미러.
--
-- source_refs는 레거시 momens-api가 소유하는 외부 테이블이다(momens-api 000002_retrieval_projection.sql).
-- 운영(prod)에서는 공유 DB에 이미 존재하므로 새 서버가 이 테이블을 만들지 않는다. prod는 Flyway를 끄고
-- ddl-auto=validate로 실제 테이블에 SourceRef 엔티티 매핑만 검증한다([데이터] docs/rules/persistence.md).
--
-- 이 마이그레이션은 local/test(별도 DB)에서만 실행되며, 읽기 엔티티 매핑 검증과 fixture 삽입을 위해
-- SourceRef가 매핑하는 컬럼만 만든다. 서버는 source_refs를 쓰지 않는다(read-only). 레거시의 workspaces FK와
-- 매핑하지 않은 컬럼(source_object_*, author_*, metadata 등)은 미러에서 생략한다.
CREATE TABLE source_refs (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    source_type TEXT NOT NULL,
    title TEXT,
    snippet TEXT,
    text TEXT,
    source_url TEXT,
    source_created_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);
