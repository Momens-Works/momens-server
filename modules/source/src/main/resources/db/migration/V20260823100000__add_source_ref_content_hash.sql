-- prod-schema: mirror
-- 운영 source_refs 테이블의 content_hash 컬럼과 부분 UNIQUE 인덱스를 local과 test 환경에도 생성합니다.
--
-- 레거시 000015_source_refs_content_hash.sql에서 worker의 중복 제거를 위해 추가한 구조입니다.
-- 지금까지 신규 서버는 해당 테이블을 읽기만 했으므로 필요하지 않았지만, MOM-0868에서 사용자가 붙여넣은 링크를
-- 저장하기 시작하면서 쓰기 대상이 됩니다. 쓰는 테이블은 운영 환경과 같은 제약을 가져야 하며,
-- 부분 UNIQUE 인덱스도 제약에 포함합니다(docs/rules/persistence.md).
--
-- 신규 서버는 content_hash를 저장하지 않습니다. 값을 저장하면 같은 주소를 두 번 연결할 때 부분 UNIQUE 인덱스가
-- 두 번째 행의 생성을 막지만, 레거시는 요청마다 새 행을 생성합니다.
ALTER TABLE source_refs ADD COLUMN content_hash TEXT;

CREATE UNIQUE INDEX uq_source_refs_content_hash
    ON source_refs(workspace_id, content_hash)
    WHERE content_hash IS NOT NULL;
