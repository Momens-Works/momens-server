-- prod 쌍둥이의 합성 데이터 (MOM-0909).
--
-- 지금까지의 리허설은 빈 DB 에서 돌았다. 빈 DB 가 재현하지 못하는 것은 둘이다.
--
--   1. 기존 행이 있어야만 드러나는 실패 — `SET NOT NULL`, CHECK 위반, UNIQUE 충돌
--   2. `tasks` 를 ACCESS EXCLUSIVE 로 잡는 구간의 실제 길이
--
-- 값은 전부 합성이다. prod 덤프를 가져오지 않는다. 형태만 맞으면 되는 자리에는 뻔한
-- 자리표시자를 쓴다.
--
-- 볼륨은 `tasks` 10 만 행으로 잡는다. 부트스트랩이 `tasks` 에 거는 것은 ADD COLUMN 세 번과
-- FK 참조 두 번인데, ADD COLUMN 은 PG11+ 에서 메타데이터 조작이라 볼륨과 무관하고 CHECK 검증만
-- 전체 스캔한다. 볼륨의 목적은 스캔을 느리게 만드는 것이 아니라 **락 보유 구간이 실제로 존재하는
-- 것을 관측 가능하게 만드는 것**이다.

BEGIN;

INSERT INTO workspaces (id, name, slug)
VALUES ('11111111-1111-1111-1111-111111111111', '쌍둥이 워크스페이스', 'twin');

INSERT INTO users (id, email, name, job_role)
SELECT ('22222222-2222-2222-2222-' || lpad(i::text, 12, '0'))::uuid,
       'user' || i || '@example.com',
       '홍길동' || i,
       (ARRAY['pm', 'design', 'backend', 'frontend'])[1 + (i % 4)]
FROM generate_series(1, 50) AS i;

INSERT INTO workspace_members (workspace_id, user_id, role)
SELECT '11111111-1111-1111-1111-111111111111', id, 'member' FROM users;

INSERT INTO projects (id, workspace_id, name, owner_id, label)
SELECT ('33333333-3333-3333-3333-' || lpad(i::text, 12, '0'))::uuid,
       '11111111-1111-1111-1111-111111111111',
       '프로젝트 ' || i,
       ('22222222-2222-2222-2222-' || lpad((1 + (i % 50))::text, 12, '0'))::uuid,
       'PRJ-' || lpad(i::text, 4, '0')
FROM generate_series(1, 20) AS i;

-- 10 만 행. deleted_at 이 있는 행과 assignee 가 없는 행을 섞는다 — 부트스트랩이 거는
-- CHECK 가 NULL 을 어떻게 다루는지가 실측 대상이기 때문이다.
INSERT INTO tasks (id, project_id, workspace_id, title, status, priority, assignee_id, deleted_at, label)
SELECT ('44444444-4444-4444-4444-' || lpad(i::text, 12, '0'))::uuid,
       ('33333333-3333-3333-3333-' || lpad((1 + (i % 20))::text, 12, '0'))::uuid,
       '11111111-1111-1111-1111-111111111111',
       '태스크 ' || i,
       (ARRAY['backlog', 'in_progress', 'done'])[1 + (i % 3)],
       (ARRAY['low', 'medium', 'high'])[1 + (i % 3)],
       CASE WHEN i % 5 = 0 THEN NULL
            ELSE ('22222222-2222-2222-2222-' || lpad((1 + (i % 50))::text, 12, '0'))::uuid END,
       CASE WHEN i % 97 = 0 THEN now() - interval '1 day' ELSE NULL END,
       'TSK-' || lpad(i::text, 6, '0')
FROM generate_series(1, 100000) AS i;

-- 레거시가 쓰는 나머지 테이블에도 행을 둔다. 부트스트랩이 직접 건드리지는 않지만
-- `ddl-auto: validate` 와 `momens_server` 의 DML 권한 확인 대상이다.
INSERT INTO source_refs (id, workspace_id, source_type, source_object_type, source_object_id,
                         source_url, title, content_hash)
SELECT ('55555555-5555-5555-5555-' || lpad(i::text, 12, '0'))::uuid,
       '11111111-1111-1111-1111-111111111111',
       'slack', 'message', 'ext-' || i,
       'https://example.com/' || i, '출처 ' || i, md5(i::text)
FROM generate_series(1, 500) AS i;

INSERT INTO refresh_tokens (id, user_id, token_hash, client_type, expires_at)
SELECT ('66666666-6666-6666-6666-' || lpad(i::text, 12, '0'))::uuid,
       ('22222222-2222-2222-2222-' || lpad(i::text, 12, '0'))::uuid,
       md5(i::text) || md5(i::text),
       'mobile',
       now() + interval '30 days'
FROM generate_series(1, 50) AS i;

COMMIT;

ANALYZE;
