SELECT name, slug, description FROM workspaces WHERE slug = 'workspace';
SELECT wm.role FROM workspace_members wm JOIN workspaces w ON w.id = wm.workspace_id
WHERE w.slug = 'workspace' ORDER BY wm.user_id;
SELECT p.label, p.name, p.description, p.status, p.health_status, p.progress,
       p.unresolved_count, p.voc_signal_count, p.metadata::text
FROM projects p JOIN workspaces w ON w.id = p.workspace_id WHERE w.slug = 'workspace';
SELECT po.owner_user_id FROM project_owners po
JOIN projects p ON p.id = po.project_id JOIN workspaces w ON w.id = p.workspace_id
WHERE w.slug = 'workspace' ORDER BY po.owner_user_id;
SELECT m.label, m.memory_type, m.title, m.body, m.status, m.metadata::text,
       m.related_entity_ids = ARRAY[p.id] AS relates_to_welcome_project
FROM confirmed_memories m
JOIN workspaces w ON w.id = m.workspace_id
JOIN projects p ON p.workspace_id = w.id
WHERE w.slug = 'workspace' ORDER BY m.label;
SELECT ls.label_prefix, ls.next_value FROM workspace_label_sequences ls
JOIN workspaces w ON w.id = ls.workspace_id WHERE w.slug = 'workspace' ORDER BY ls.label_prefix;
