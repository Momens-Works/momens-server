-- 태스크 역할은 다중 선택이 아니라 단일 선택이다(2026-07-07 확정, MOM-76). task_roles 조인
-- 테이블 대신 tasks에 단일 role 컬럼을 둔다.
ALTER TABLE tasks ADD COLUMN role TEXT;

-- 기존에 역할이 여러 개 있던 행(local/test 데이터뿐)은 알파벳 순 첫 값을 살린다.
UPDATE tasks t
SET role = (SELECT tr.role FROM task_roles tr WHERE tr.task_id = t.id ORDER BY tr.role LIMIT 1);

-- 역할이 아예 없던 행은 기본값으로 채운다.
UPDATE tasks SET role = 'pm' WHERE role IS NULL;

ALTER TABLE tasks
    ALTER COLUMN role SET NOT NULL,
    ADD CONSTRAINT tasks_role_check
        CHECK (role IN ('pm', 'design', 'backend', 'frontend', 'android', 'qa'));

DROP TABLE task_roles;