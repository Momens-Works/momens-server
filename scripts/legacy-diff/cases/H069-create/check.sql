SELECT from_entity_type, from_entity_id, relation_type, to_entity_type, to_entity_id, (deleted_at IS NULL) AS not_deleted
FROM entity_relations WHERE workspace_id = '00000000-0000-4000-8000-000000000012' ORDER BY to_entity_type, to_entity_id;
SELECT source_type, source_object_type, source_object_id, source_url, title, visibility, metadata::text, content_hash
FROM source_refs WHERE workspace_id = '00000000-0000-4000-8000-000000000012' ORDER BY source_object_id;
