-- 변경할 값이 없는 요청. 레거시는 updated_at 을 갱신하고 신규 서버는 갱신하지 않습니다
-- (docs/design/legacy-product-api-migration/ledger.md H024 행). 그 차이를 드러내는 것이
-- 이 케이스의 목적이므로 updated_at 을 일부러 비교 대상에 넣습니다.
SELECT name, slug, description, created_at, updated_at
FROM workspaces
WHERE id = '00000000-0000-4000-8000-000000000011';
