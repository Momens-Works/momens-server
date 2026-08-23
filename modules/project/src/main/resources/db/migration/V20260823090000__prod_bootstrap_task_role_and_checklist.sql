-- prod 부트스트랩 보정(MOM-0909). prod에서 실행할 수 없는 마이그레이션 세 건의 **순효과만** 만든다.
--
-- 아래 세 파일은 부트스트랩에서 실행하지 않고 flyway_schema_history에 적용된 것으로 심는다.
--   V20260707120000__add_task_detail_and_checklist.sql  (부분 충돌)
--   V20260707150000__task_role_single_value.sql          (실행 불가)
--   V20260715090000__task_role_drop_not_null.sql         (위 파일에 의존)
--
-- 심는 이유는 파일마다 다르다.
--
-- V20260707120000 — `tasks.description`·`assignee_id`와 인덱스 `idx_tasks_assignee_id`가 레거시
--   `000001_init.sql`에 이미 있어 그대로 실행하면 중복 생성으로 죽는다. 이 파일에서 prod에 없는 것은
--   `task_checklist_items`와 그 인덱스뿐이다.
--
-- V20260707150000 — `task_roles`를 읽어 `tasks.role`을 백필하고 `DROP TABLE task_roles`로 끝나는데,
--   **`task_roles`는 prod에 존재한 적이 없다.** 그 테이블을 만드는 것은 이 리포의
--   `V20260706120000__create_task.sql`이고 local/test 전용이다(해당 파일 7행 주석이 그렇게 적고 있다).
--   `UPDATE`가 `relation "task_roles" does not exist`로 죽고, 그것을 넘겨도 기존 `tasks` 행의 `role`이
--   전부 NULL이라 `SET NOT NULL`에서 다시 죽는다. `DROP TABLE`이 들어 있어 되돌릴 수도 없다.
--
-- V20260715090000 — 위 파일이 만든 컬럼의 `NOT NULL`을 푸는 파일이라 단독으로 의미가 없다.
--
-- 따라서 두 role 파일의 prod 순효과는 `tasks.role TEXT`(nullable) + `tasks_role_check` 뿐이다.
-- `NOT NULL`을 거쳤다 푸는 중간 단계와 `task_roles` 조인 테이블은 prod에 존재한 적이 없으므로
-- 재현하지 않는다. 백필도 하지 않는다 — 읽어 올 원본이 없고, 값을 지어내지 않는다.
-- 기존 prod 행의 `role`은 NULL로 남는다(레거시 웹은 role 없이 태스크를 만들고, 모바일은 NULL을
-- "미지정"으로 렌더한다). 같은 판단이 momens-api#28의 `000019`에 남아 있다.
--
-- **local/dev에서는 위 세 파일이 이미 실행됐으므로 이 파일이 만드는 객체가 전부 존재한다.**
-- 그래서 모든 구문을 idempotent하게 쓴다([데이터](../../../../../../docs/rules/persistence.md)의
-- "대상 객체가 이미 있는 환경" 규칙). prod에서만 실제로 무언가를 만든다.

ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS role TEXT;

-- ADD CONSTRAINT 에는 IF NOT EXISTS 가 없다. local/dev 에는 V20260707150000 이 만든 같은 이름의
-- 제약이 이미 있으므로 존재 여부를 보고 건다.
--
-- conrelid 로 테이블을 함께 본다. Postgres 는 제약 이름을 테이블 단위로만 유일하게 강제하므로
-- (pg_constraint 의 유니크 인덱스가 conrelid, contypid, conname), 이름만 보면 다른 테이블의 동명
-- 제약에 걸려 tasks 에는 CHECK 가 붙지 않은 채 조용히 넘어간다. 마이그레이션은 성공하고
-- ddl-auto=validate 는 제약을 보지 않으므로 아무도 모른다.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'tasks_role_check'
           AND conrelid = 'tasks'::regclass
    ) THEN
        ALTER TABLE tasks
            ADD CONSTRAINT tasks_role_check
            CHECK (role IN ('pm', 'design', 'backend', 'frontend'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS task_checklist_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    position INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_task_checklist_items_task_id
    ON task_checklist_items(task_id);
