SELECT user_id, role, created_at, updated_at
FROM workspace_members
WHERE workspace_id = '00000000-0000-4000-8000-000000000012'
ORDER BY user_id;
