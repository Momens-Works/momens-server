-- 사용자 로그인 수단(MOM-0830, docs/adr/0016-user-identity-key-google-sub.md).
--
-- 로그인 시 사용자 식별 기준을 이메일에서 (provider, provider_user_id)로 옮기기 위한 테이블이다.
-- 이메일은 Google이 관리하므로 변경될 수 있다. 이메일이 바뀌면 ON CONFLICT (email)이 적용되지 않아
-- 같은 사용자에 대해 새 users 행이 생성되고, 기존 멤버십과 담당 관계가 끊길 수 있다.
--
-- 이 서버가 새로 소유하고 사용하는 테이블이며, prod 반영은 레거시 마이그레이션(MOM-0831)이 담당한다
-- (docs/rules/persistence.md). 이 파일은 이 서버가 스키마를 소유하는 local/test/dev 환경에서만 실행된다.
--
-- users FK를 두는 것은 signal_actions·push_installations·minsu_task_draft_generations에서 외부 테이블
-- FK를 생략한 것과는 다르다. user_identities와 users는 같은 user 모듈이 소유하므로 FK가 모듈 내부에
-- 한정되고, 신규 사용자 생성 시 두 행도 하나의 트랜잭션에서 함께 생성한다(ADR-0016 결정 2).
-- ON DELETE CASCADE는 사용자가 삭제될 때 해당 사용자의 로그인 수단도 함께 삭제하기 위해 둔다.
--
-- provider는 서버가 허용하는 값의 범위를 관리하므로 CHECK 제약을 둔다. 현재는 'google'만 허용하며,
-- 로그인 수단을 추가할 때 허용 값도 함께 늘린다. 반대로 source_refs.source_type처럼 생산자가 새로운
-- 값을 추가하는 컬럼에는 CHECK를 두지 않는다.
--
-- 컬럼명을 sub으로 두지 않은 이유는 ADR-0003에서 우리 access token의 sub을 내부 사용자 ID로
-- 정의하고 있기 때문이다. 같은 코드베이스에서 sub이 서로 다른 의미로 쓰이는 것을 피한다.
--
-- UNIQUE (provider, provider_user_id)는 하나의 Google 계정이 여러 사용자에게 중복 연결되는 것을
-- DB에서 막는다. 신규 사용자 생성 요청이 동시에 들어오는 경우도 이 제약으로 방어한다
-- (ADR-0016 결정 3).
--
-- id DEFAULT는 앱을 거치지 않는 경우에도 ID가 생성되도록 둔다. 식별자는 앱(BaseEntity)에서 생성해
-- 전달하므로 이 기본값은 시드나 수동 SQL처럼 앱을 거치지 않는 경우에만 사용된다. 최근 서버 소유
-- 테이블인 push_installations와 minsu_task_draft_generations에서는 DEFAULT를 생략했지만, 이 테이블에
-- 둔 것은 PR #131 리뷰 의견을 ADR-0016 결정 2에 반영한 결과다.
CREATE TABLE user_identities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider TEXT NOT NULL CHECK (provider IN ('google')),
    provider_user_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_user_id)
);
