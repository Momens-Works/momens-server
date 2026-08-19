-- owner 유저는 이 워크스페이스(…0012)의 member 역할이라 H024 가 요구하는 admin/owner 요건에
-- 미달합니다. 비멤버 경로는 H022-forbidden(…0013)이 맡고, 여기는 "역할 부족" 경로입니다.
-- 어느 쪽이든 양쪽 모두 아무것도 기록하지 않아야 하므로 픽스처 값 그대로인지 updated_at 까지
-- 비교합니다.
SELECT name, slug, description, created_at, updated_at
FROM workspaces
WHERE id = '00000000-0000-4000-8000-000000000012';
