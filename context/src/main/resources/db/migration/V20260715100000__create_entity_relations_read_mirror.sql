-- local/test 전용 entity_relations 테이블.
--
-- entity_relations는 레거시 momens-api가 소유하는 외부 테이블이다
-- (momens-api: 000002_retrieval_projection.sql).
--
-- 운영(prod)에서는 공유 DB에 이미 존재하므로 새 서버가 생성하지 않는다.
-- prod는 Flyway를 비활성화하고 ddl-auto=validate로 실제 테이블과
-- EntityRelation 엔티티 매핑만 검증한다([데이터] docs/rules/persistence.md).
--
-- 이 마이그레이션은 local/test 전용으로, 별도 DB에서 읽기 엔티티 매핑 검증과
-- fixture 구성을 위해 EntityRelation이 매핑하는 컬럼만 생성한다.
-- 서버는 entity_relations를 읽기 전용으로만 사용한다.
-- 레거시의 workspaces FK와 EntityRelation이 매핑하지 않는 컬럼
-- (weight, source_ref_ids, metadata, updated_at)은 생성하지 않는다.
CREATE TABLE entity_relations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    from_entity_type TEXT NOT NULL,
    from_entity_id UUID NOT NULL,
    relation_type TEXT NOT NULL,
    to_entity_type TEXT NOT NULL,
    to_entity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);