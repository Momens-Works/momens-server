-- prod-schema: required MOM-0840
-- local/test 전용 signal_evidence 미러.
--
-- signal_evidence는 worker가 Signal 생성 시 함께 적재하는 근거 연결이다(ADR-0007). Signal과 source_refs를
-- 잇는 조인 테이블이고, 서버는 읽기만 한다. api-server는 sort_order 순으로 source_ref_id를 얻어 source
-- 모듈로 근거 상세를 hydrate한다.
--
-- signals 미러와 같은 방식으로 local/test(별도 DB)에서만 만들고, prod 스키마 반영 위치는 MOM-74에서 확정한다.
-- 외부 테이블(workspaces/signals/source_refs) FK는 미러에서 생략한다.
CREATE TABLE signal_evidence (
    workspace_id UUID NOT NULL,
    signal_id UUID NOT NULL,
    source_ref_id UUID NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (signal_id, source_ref_id)
);
