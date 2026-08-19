-- 권한이 없으면 양쪽 모두 아무것도 기록하지 않아야 합니다. 픽스처 값 그대로여야 하므로
-- updated_at 까지 비교합니다.
SELECT name, slug, description, created_at, updated_at
FROM workspaces
WHERE id = '00000000-0000-4000-8000-000000000012';
