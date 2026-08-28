-- slug 검증에서 막히면 양쪽 모두 workspaces 를 건드리지 않아야 합니다.
SELECT name, slug, description, created_at, updated_at
FROM workspaces
WHERE id = '00000000-0000-4000-8000-000000000011';
