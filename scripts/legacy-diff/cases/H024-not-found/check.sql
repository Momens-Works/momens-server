-- 존재하지 않는 id 에 대한 PATCH 가 엉뚱한 행을 건드리지 않았는지 확인합니다. 대상 행이 없으므로
-- 픽스처의 다른 워크스페이스를 봅니다.
SELECT name, slug, description, created_at, updated_at
FROM workspaces
WHERE id = '00000000-0000-4000-8000-000000000011';
