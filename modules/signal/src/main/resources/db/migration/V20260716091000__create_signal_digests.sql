-- prod-schema: required MOM-0840
-- local/test 전용 signal_digests 미러.
--
-- signal_digests는 브리프의 "시그널 요약" 헤더 아래 문단이다(2026-07-10 화면설계서 /브리프 2번). 그날 신호를
-- 한 문단으로 요약한 민수 산출물이라 서버는 쓰지 않고 읽기만 한다(SignalDigest 엔티티는 @Immutable). 민수 구현
-- 전에는 같은 backing 계약을 따르는 fixture가 채운다(ADR-0011).
--
-- 하루 단위 요약이지만 날짜 컬럼을 두지 않고 created_at으로 거른다. 브리프가 시그널을 거를 때 쓰는 기준과
-- 같은 컬럼, 같은 규칙이라 문단과 그 문단이 설명하는 시그널이 어긋날 수 없다. 하루 경계를 어떤 타임존으로
-- 볼지는 mobile의 BriefDay가 단일 출처로 소유하므로, 이 테이블과 생산자는 타임존을 몰라도 된다. 같은 범위에
-- 여러 건이 있으면 조회는 가장 최근 것을 쓴다(민수가 다시 만들면 최신이 이긴다).
--
-- signals 미러와 같은 방식으로 외부(민수)가 쓰는 테이블이라 workspaces/projects FK는 미러에서 생략하고,
-- 식별자도 앱이 만들지 않아 id DEFAULT를 두지 않는다. 운영(prod) 공유 스키마 반영 위치는 signals와 함께
-- MOM-74에서 확정한다([데이터] docs/rules/persistence.md).
--
-- workspace_id와 deleted_at은 signals·signal_evidence·source_refs 미러와 같은 관례다. workspace_id는
-- 교차 워크스페이스 노출을 쿼리 단계에서 한 번 더 막는 방어 스코프이고(멤버십 검사와 별개로, signals
-- 조회와 같은 방어), deleted_at은 서버가 쓰지 않는 이 테이블에서 생산자가 문단을 철회할 수 있는 유일한
-- 경로다. 조회는 소프트 삭제를 없는 것으로 취급한다.
CREATE TABLE signal_digests (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    summary TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

-- 조회는 프로젝트 스코프 + 생성 시각 범위 + 최신순(동률 시 id 내림차순)이고 소프트 삭제는 제외한다.
CREATE INDEX idx_signal_digests_project_created
    ON signal_digests(project_id, created_at DESC) WHERE deleted_at IS NULL;
