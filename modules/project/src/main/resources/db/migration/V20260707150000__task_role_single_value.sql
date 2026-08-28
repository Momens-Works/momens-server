-- prod-schema: required MOM-0840
-- 태스크 역할은 다중 선택이 아니라 단일 선택이다(2026-07-07 확정, MOM-76). task_roles 조인
-- 테이블 대신 tasks에 단일 role 컬럼을 둔다.
ALTER TABLE tasks ADD COLUMN role TEXT;

-- 기존에 역할이 여러 개 있던 행(local/test 데이터뿐)은 알파벳 순 첫 값을 살린다. 역할이 아예 없던
-- 행은 임의 값으로 채우지 않는다. 역할 누락은 생성 API가 400(COMMON_VALIDATION_FAILED)으로 막는
-- 불변이라, 그런 행이 남아 있으면 SET NOT NULL이 실패해 문제를 드러낸다(규일 리뷰, 기본값을 지어내지 않는다).
UPDATE tasks t
SET role = (SELECT tr.role FROM task_roles tr WHERE tr.task_id = t.id ORDER BY tr.role LIMIT 1);

ALTER TABLE tasks
    ALTER COLUMN role SET NOT NULL,
    ADD CONSTRAINT tasks_role_check
        CHECK (role IN ('pm', 'design', 'backend', 'frontend'));

DROP TABLE task_roles;
