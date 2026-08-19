-- 값이 실제로 바뀌는 요청. 양쪽 모두 갱신하므로 updated_at 은 각자의 벽시계라 비교하지 않습니다.
-- 반영된 값 자체가 검증 대상입니다.
SELECT name, slug, description, created_at
FROM workspaces
WHERE id = '00000000-0000-4000-8000-000000000011';
