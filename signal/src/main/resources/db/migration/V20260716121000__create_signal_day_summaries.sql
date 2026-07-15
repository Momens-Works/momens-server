-- worker(민수) 산출물인 프로젝트별 하루 시그널 요약 문단의 read 미러(MOM-0787).
--
-- signal_day_summaries는 signals·signal_evidence(momens-worker가 채우는 기존 미러)와 달리 아직 worker가
-- 요약을 생산하지 않는다. worker 구현 전까지는 같은 backing 계약의 fixture(dev 시드)가 채운다
-- (docs/design/mobile-mvp-server-requirements.md "합성/파생 필드 응답 정책"). 서버는 production 코드에
-- 별도 분기를 두지 않고 항상 이 테이블을 조회하며, 값이 없으면 null을 그대로 응답한다.
--
-- summary_date는 소비 표면(mobile BriefDay, Asia/Seoul)이 정한 하루 경계의 날짜를 그대로 저장한다. 서버는
-- 조회 시 별도로 타임존을 재해석하지 않으므로, 생산 단계가 같은 하루 경계를 맞춰 채워야 한다.
--
-- prod에 이 테이블을 어디에 둘지(신규 서버 전용 DB인지 공유 DB 신규 테이블인지)는 signals 미러와 같이 추후 확정한다.
-- 지금은 local/test 전용으로 Flyway가 이 컬럼으로 만든다.
CREATE TABLE signal_day_summaries (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    summary_date DATE NOT NULL,
    summary TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    UNIQUE (project_id, summary_date)
);
