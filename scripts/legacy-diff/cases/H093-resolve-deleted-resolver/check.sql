SELECT id, label, status, deleted_at IS NOT NULL AS soft_deleted
FROM confirmed_memories WHERE workspace_id = '00000000-0000-4000-8000-000000000012' ORDER BY label;
SELECT count(*) AS relations FROM entity_relations WHERE workspace_id = '00000000-0000-4000-8000-000000000012';
