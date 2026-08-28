-- prod-schema: mirror
-- local/test 전용 entity_relations 테이블.
--
-- entity_relations는 레거시 momens-api가 소유하는 외부 테이블이다
-- (momens-api: 000002_retrieval_projection.sql).
--
-- 운영(prod)에서는 공유 DB에 이미 존재하므로 새 서버가 생성하지 않는다.
-- prod는 Flyway를 비활성화하고 ddl-auto=validate로 실제 테이블과
-- EntityRelation 엔티티 매핑만 검증한다([데이터] docs/rules/persistence.md).
--
-- dev(momens-k8s-dev)에도 이미 존재한다: dev-schema-gap.sql이 레거시
-- 000002_retrieval_projection.sql을 그대로 옮겨와 CREATE TABLE IF NOT EXISTS로
-- 미리 만들어 둔다(레거시 원본 스키마, workspace_id FK·weight·source_ref_ids·
-- metadata 컬럼 포함). 이 마이그레이션이 IF NOT EXISTS 없이 실행되면 dev
-- 실배포에서 "relation already exists"로 Flyway가 죽는다(MOM-0795). local/test는
-- 매번 빈 DB라 이 충돌이 재현되지 않았다.
--
-- 이 마이그레이션은 local/test 전용으로, 별도 DB에서 읽기 엔티티 매핑 검증과
-- fixture 구성을 위해 사용한다. 서버는 entity_relations를 읽기 전용으로만 사용한다.
-- IF NOT EXISTS라 dev처럼 테이블이 이미 있으면 스킵하고, local/test처럼 없으면
-- 아래 스키마로 새로 만든다(dev-schema-gap.sql과 같은 방식).
--
-- 감사 필드(created_at, updated_at)는 모든 테이블에 NOT NULL로 둔다([데이터]
-- docs/rules/persistence.md). updated_at은 EntityRelation이 매핑하지 않지만 레거시
-- 실제 테이블에 있는 컬럼이라, local/test도 같은 스키마를 갖도록 함께 생성한다.
-- signal_evidence 미러와 같은 방식이다.
--
-- 레거시의 workspaces FK와 EntityRelation이 매핑하지 않는 컬럼
-- (weight, source_ref_ids, metadata)은 생성하지 않는다(신규 생성 시에만 해당 —
-- dev처럼 이미 있는 테이블은 그 컬럼들을 그대로 가진 채 남는다. EntityRelation이
-- 매핑하지 않는 컬럼이라 무해하다).
CREATE TABLE IF NOT EXISTS entity_relations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    from_entity_type TEXT NOT NULL,
    from_entity_id UUID NOT NULL,
    relation_type TEXT NOT NULL,
    to_entity_type TEXT NOT NULL,
    to_entity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
