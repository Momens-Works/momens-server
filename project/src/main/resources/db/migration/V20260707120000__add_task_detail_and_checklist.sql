-- 모바일 태스크 상세(MOM-63)가 읽고, 수정(MOM-75)이 쓰는 저장 기반을 추가합니다.
-- description, assignee_id 는 레거시 momens-api 000001_init.sql 의 tasks 원형 그대로입니다.
-- 모바일이 안 쓰는 레거시 컬럼(milestone_id, due_date)은 웹 이관에서 추가합니다.

ALTER TABLE tasks
    ADD COLUMN description TEXT,
    ADD COLUMN assignee_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_tasks_assignee_id ON tasks(assignee_id) WHERE deleted_at IS NULL;

-- 완료기준(checklist)은 레거시에 없는 신규 테이블입니다. task_roles 처럼 prod 공유 스키마
-- 반영은 컷오버 시 레거시 마이그레이션으로 조율합니다(docs/pending-decisions.md P12).
-- position 은 JPA @OrderColumn 이 소유하는 리스트 순서(0부터 연속)입니다. 중간 삽입/삭제 시
-- 행별 UPDATE 로 재번호하는 과도 상태가 있어 (task_id, position) UNIQUE 는 두지 않습니다.

CREATE TABLE task_checklist_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    position INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_task_checklist_items_task_id ON task_checklist_items(task_id);