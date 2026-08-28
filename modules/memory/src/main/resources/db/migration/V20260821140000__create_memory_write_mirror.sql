-- prod-schema: mirror
-- 레거시 momens-api 000003_memory.sql 의 review_actions 미러입니다. 후보 리뷰 write(H084~H088)가
-- 액션을 남기는 감사 로그이고, 레거시에서도 INSERT 만 있고 SELECT 가 없어 read 기반(MOM-0860)에서는
-- 만들지 않았던 테이블입니다.
--
-- CREATE TABLE IF NOT EXISTS 인 이유는 dev·prod 가 레거시와 같은 DB 를 쓰고 이 테이블이 거기 이미
-- 있기 때문입니다. 조건 없이 만들면 실배포 Flyway 가 relation already exists 로 죽습니다
-- (entity_relations, MOM-0795). local/test 는 매번 빈 DB 라 이 충돌이 재현되지 않습니다.
--
-- 레거시의 FK 세 개를 그대로 둡니다. 이 서버가 쓰는 테이블이라 우리 INSERT 가 고아 행을 만들 수 있고
-- prod 가 그것을 거부하기 때문입니다(docs/rules/persistence.md 미러 기준). 읽기 전용 미러가 FK 를
-- 빼는 것과 기준이 다릅니다. workspaces·users 마이그레이션은 :memory 가 :workspace 에 의존하면서
-- 이미 테스트 클래스패스에 올라옵니다.
--
-- 인덱스는 만들지 않습니다. 계획을 테스트로 고정할 때만 만든다는 기준이고, 레거시가 이미 같은 이름의
-- idx_review_actions_candidate_id 를 갖고 있어 공유 DB 에서 충돌하기도 합니다.
--
-- 라벨 UNIQUE 제약도 걸지 않습니다. 레거시 memory_candidates·confirmed_memories 에 없는 제약이라
-- 미러가 prod 보다 엄격해지고, prod 에 실재할 수 있는 상태를 픽스처가 못 만들게 됩니다.
CREATE TABLE IF NOT EXISTS review_actions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES memory_candidates(id) ON DELETE CASCADE,
    action_type TEXT NOT NULL,
    reviewer_user_id UUID NOT NULL REFERENCES users(id),
    edited_title TEXT,
    edited_summary TEXT,
    edited_body TEXT,
    rejection_reason TEXT,
    merge_target_memory_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
