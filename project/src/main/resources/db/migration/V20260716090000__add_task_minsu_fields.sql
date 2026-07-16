-- local/test와 dev에 태스크 상세의 민수 산출물 backing을 추가한다: 다음행동(next_action)과 열린질문(task_open_questions).
--
-- 두 값은 민수가 생산하고 api-server는 읽기만 한다(2026-07-08 화면설계서 task_002 8번, 9번 interaction X,
-- docs/spec/mobile-api.md 수정 API가 보존하는 필드). 민수 구현 전까지는 같은 backing 계약을 따르는
-- fixture가 채운다(ADR-0011). 서버가 쓰지 않으므로 앱이 UUID를 만들지 않고, id DEFAULT도 두지 않는다.
--
-- 화면설계서의 글자수(열린질문 50자, 다음행동 100자)는 생산 단계 계약이라 CHECK로 미러에서 강제해
-- fixture가 계약을 어기면 삽입이 실패하게 한다(NULL은 CHECK를 통과한다). api-server는 검증하거나 자르지
-- 않고 저장된 값을 그대로 반환한다. signal_evidence 의미 필드와 같은 방식이다.
--
-- prod 공유 스키마는 레거시 momens-api가 단일 소유하므로(docs/rules/persistence.md) 이 파일은 local/test와
-- dev(application-dev.yml Flyway on)에서만 실행된다. prod 반영 위치는 task_checklist_items와 함께 MOM-74에서
-- 확정한다.
ALTER TABLE tasks
    ADD COLUMN next_action TEXT CHECK (char_length(next_action) <= 100);

-- sort_order는 표시 순서다. 완료기준의 position과 달리 앱이 0부터 연속으로 부여하지 않고 생산자가 주는
-- 값이라, 값이 비거나 겹칠 수 있어 조회에서 id로 보조 정렬한다(signal_evidence.sort_order와 같은 성격).
CREATE TABLE task_open_questions (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    body TEXT NOT NULL CHECK (char_length(body) <= 50),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_task_open_questions_task_id ON task_open_questions(task_id);
