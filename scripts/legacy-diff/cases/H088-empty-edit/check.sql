SELECT status, reviewed_by_user_id, rejection_reason FROM memory_candidates WHERE id = '00000000-0000-4000-8000-000000000051';
SELECT label, memory_type, title, summary, body, status, source_ref_ids, related_entity_ids,
       created_from_candidate_id, confirmed_by_user_id, metadata
FROM confirmed_memories WHERE created_from_candidate_id = '00000000-0000-4000-8000-000000000051';
SELECT action_type, reviewer_user_id, edited_title, edited_summary, edited_body,
       rejection_reason, merge_target_memory_id
FROM review_actions WHERE candidate_id = '00000000-0000-4000-8000-000000000051';
