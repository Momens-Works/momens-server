-- prod-schema: required MOM-0840
-- push 설치(FID/FCM token) 상태(MOM-0690, docs/design/signal-push-demo-design.md 8.4절).
--
-- 레거시 미러가 아니라 이 서버가 소유하는 신규 상태다. prod 스키마 반영 경로는 prod 배포와 함께
-- 범위 밖이며 반영 시점에 별도 확정한다. 외부 테이블(users) FK는 다른 테이블과 같은 이유로 생략한다.
--
-- FID(firebase_installation_id)가 설치본의 canonical identity라 UNIQUE이고, 활성 FCM token은
-- 중복을 허용하지 않아 활성 행 부분 unique 제약으로 강제한다. 로그아웃·무효 token은 물리 삭제 대신
-- active=false + deactivated_at으로 반영한다.
CREATE TABLE push_installations (
    id UUID PRIMARY KEY,
    firebase_installation_id TEXT NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    fcm_registration_token TEXT NOT NULL,
    platform TEXT NOT NULL CHECK (platform IN ('android')),
    active BOOLEAN NOT NULL,
    deactivated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_push_installations_active_token
    ON push_installations (fcm_registration_token) WHERE active;

-- 수신자 결정이 사용자 목록으로 활성 설치를 조회한다(9절).
CREATE INDEX idx_push_installations_active_user
    ON push_installations (user_id) WHERE active;
