-- prod-schema: mirror
-- 레거시 momens-api의 000010_source_credentials.sql에서 생성한 source_credentials 테이블과 동일한
-- 스키마를 구성합니다. 앞선 마이그레이션과 마찬가지로 운영 환경에는 테이블이 이미 존재하며, 이 파일은
-- local과 test 환경에서만 실행됩니다.
--
-- access_token_enc와 refresh_token_enc에는 provider가 발급한 토큰을 암호화해 저장합니다. 저장 형식은
-- nonce를 앞에 붙인 AES-256-GCM 바이트 배열이며, 이를 그대로 BYTEA 컬럼에 저장합니다.
--
-- 이 암호화 형식은 신규 서버만의 기준으로 변경할 수 없습니다. momens-worker가 같은 키로 값을 복호화해
-- 외부 source를 수집하기 때문입니다. 바이트 배치가 다르면 신규 서버에서 생성한 연결을 worker가 사용할
-- 수 없습니다.
--
-- connection_id의 외래 키는 같은 모듈이 소유한 테이블을 참조하므로 유지합니다. 다른 모듈을 향하는
-- 참조는 추가되지 않습니다.
CREATE TABLE source_credentials (
    connection_id UUID PRIMARY KEY REFERENCES source_connections(id) ON DELETE CASCADE,
    access_token_enc BYTEA NOT NULL,
    refresh_token_enc BYTEA,
    token_type TEXT,
    scope TEXT,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
