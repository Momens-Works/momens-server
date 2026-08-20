-- prod-schema: mirror
-- 레거시 momens-api의 000004_source_connections.sql과 000006_fe_contract.sql에서 생성한
-- source_connections 테이블과 동일한 스키마를 구성합니다.
--
-- 이 테이블은 레거시 서버가 소유하며 운영 환경의 공유 데이터베이스에 이미 존재합니다. 따라서 이
-- 마이그레이션은 local과 test 환경에서만 실행되며 운영 스키마에는 적용되지 않습니다. 운영 환경에서는
-- Flyway를 비활성화하고 ddl-auto=validate를 사용해 엔티티와 테이블의 매핑만 검증합니다.
--
-- 읽기 전용 테이블과 달리 이 테이블에는 신규 서버가 쓰기 작업을 수행합니다. 사용자가 provider의 동의
-- 화면에서 승인하면 provider가 신규 서버로 브라우저를 돌려보내는데, 그때 들어오는 요청(H082)이 연결
-- 정보를 생성하거나 갱신합니다. 신규 서버가 운영 환경에서 거부될 데이터를 생성하지 않도록 NOT NULL,
-- CHECK, 외래 키 제약을 레거시와 동일하게 구성합니다.
--
-- workspace_id, source_type, external_workspace_id 조합에는 UNIQUE 제약을 추가하지 않습니다. 레거시에도
-- 해당 제약이 없습니다. 레거시는 세 컬럼을 기준으로 갱신을 먼저 시도하고, 대상이 없으면 새 행을
-- 삽입합니다. UNIQUE 제약이 없으므로 같은 조합의 요청이 동시에 들어오면 중복 행이 생성될 수 있으며,
-- 이 동작을 유지합니다.
--
-- captures_read_count와 candidates_extracted_count는 momens-worker가 갱신하며, 신규 서버는 해당 값에
-- 대해 읽기 작업만 수행 합니다.
CREATE TABLE source_connections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    source_type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED', 'ERROR', 'REVOKED')),
    external_workspace_id TEXT,
    external_workspace_name TEXT,
    connected_by_user_id UUID REFERENCES users(id),
    connected_at TIMESTAMPTZ,
    last_synced_at TIMESTAMPTZ,
    disabled_at TIMESTAMPTZ,
    resync_requested_at TIMESTAMPTZ,
    captures_read_count BIGINT NOT NULL DEFAULT 0 CHECK (captures_read_count >= 0),
    candidates_extracted_count BIGINT NOT NULL DEFAULT 0 CHECK (candidates_extracted_count >= 0),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_source_connections_workspace_id ON source_connections(workspace_id);
