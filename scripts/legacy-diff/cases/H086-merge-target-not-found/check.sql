SELECT status, reviewed_at, reviewed_by_user_id FROM memory_candidates WHERE id = '00000000-0000-4000-8000-000000000051';
SELECT count(*) AS review_actions FROM review_actions WHERE candidate_id = '00000000-0000-4000-8000-000000000051';
