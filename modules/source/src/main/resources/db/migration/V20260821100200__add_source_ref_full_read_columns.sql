-- prod-schema: mirror
-- source_refs 테이블에 아직 생성되지 않은 source_connection_id, source_updated_at, metadata 컬럼을
-- 추가합니다.
--
-- H096 검증 endpoint는 source-ref 한 건을 레거시와 동일한 필드 구성으로 반환합니다. 기존 로컬 스키마에는
-- 근거 카드와, 태스크에 연결된 메모리와 source-ref 목록에서 사용하는 컬럼만 있어 해당 응답을 완성할 수
-- 없습니다.
--
-- 이 마이그레이션이 적용되면 source_refs는 레거시와 동일한 컬럼 집합을 갖습니다. 이후 이 테이블에 컬럼을
-- 추가하는 경우는 레거시 스키마에 컬럼이 먼저 추가되었을 때로 한정합니다.
--
-- source_connection_id에는 외래 키를 추가하지 않습니다. 레거시에도 해당 외래 키가 없습니다.
ALTER TABLE source_refs
    ADD COLUMN source_connection_id UUID,
    ADD COLUMN source_updated_at TIMESTAMPTZ,
    ADD COLUMN metadata JSONB;
