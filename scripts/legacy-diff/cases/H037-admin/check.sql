SELECT label, name, status, owner_id, health_status, progress, unresolved_count, voc_signal_count, target_date, summary
FROM projects WHERE workspace_id = '00000000-0000-4000-8000-000000000012' AND label = 'PRJ-0002';
SELECT po.owner_user_id FROM project_owners po JOIN projects p ON p.id = po.project_id
WHERE p.workspace_id = '00000000-0000-4000-8000-000000000012' AND p.label = 'PRJ-0002'
ORDER BY po.owner_user_id;
