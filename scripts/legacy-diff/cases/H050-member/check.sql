SELECT name, status, health_status, progress, target_date
FROM milestones WHERE project_id = '00000000-0000-4000-8000-000000000021' AND name = '태스크 목록 조회 API 마감';
SELECT mo.owner_user_id FROM milestone_owners mo JOIN milestones m ON m.id = mo.milestone_id
WHERE m.name = '태스크 목록 조회 API 마감' ORDER BY mo.owner_user_id;
