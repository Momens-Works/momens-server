-- prod-schema: mirror
-- 레거시 momens-api의 000011_workspace_invitations.sql에서 생성한 workspace_invitations
-- 테이블과 동일한 스키마를 구성합니다.
--
-- 이 테이블은 레거시 서버가 소유하며 운영 환경의 공유 데이터베이스에 이미 존재합니다. 따라서 이
-- 마이그레이션은 local과 test 환경에서만 실행되며 운영 스키마에는 적용되지 않습니다.
--
-- 신규 서버도 이 테이블에 쓰기 작업을 수행하므로 NOT NULL, CHECK, 외래 키 제약을 레거시와
-- 동일하게 구성합니다.
--
-- 마지막에 정의한 UNIQUE 인덱스는 초대 생성 동작을 결정합니다. 초대를 생성할 때 이 인덱스를
-- 대상으로 ON CONFLICT를 적용해, 같은 워크스페이스에 같은 이메일로 대기 중인 초대가 있으면 새 행을
-- 생성하지 않고 기존 행을 갱신합니다. 이 인덱스가 없으면 동일한 초대가 중복으로 저장될 수 있습니다.
CREATE TABLE IF NOT EXISTS workspace_invitations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('admin', 'member')),
    inviter_id UUID REFERENCES users(id),
    token_hash TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'revoked')),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    last_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_workspace_invitations_token_hash ON workspace_invitations(token_hash);
CREATE UNIQUE INDEX IF NOT EXISTS idx_workspace_invitations_pending_email_unique
    ON workspace_invitations(workspace_id, lower(email))
    WHERE status = 'pending';
