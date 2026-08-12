-- prod-schema: mirror
-- 레거시 momens-api 000006_fe_contract.sql 의 workspace_label_sequences 테이블과 호환됩니다.
-- 레거시는 next_workspace_label() 함수와 BEFORE INSERT 트리거로 라벨을 자동 발급했지만,
-- 새 서버는 트리거를 두지 않고 project 모듈이 workspace 모듈 public API(LabelAllocator)로
-- 명시적으로 발급받습니다. 원자적 발급은 ON CONFLICT UPSERT 한 문장으로 처리합니다.
-- label_prefix는 레거시와 동일하게 SUG/MEM/MOM을 허용해 memory 모듈이 같은 테이블을 재사용합니다.
-- created_at은 레거시 원본에 없어서 두지 않습니다. 발급 카운터라 행이 언제 처음 만들어졌는지보다
-- 마지막 발급 시각(updated_at)이 의미가 있고, 레거시 스키마와의 호환을 우선합니다.
CREATE TABLE workspace_label_sequences (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    label_prefix TEXT NOT NULL CHECK (label_prefix IN ('SUG', 'MEM', 'MOM')),
    next_value BIGINT NOT NULL DEFAULT 1 CHECK (next_value > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (workspace_id, label_prefix)
);
