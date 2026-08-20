-- 레거시 차등 비교 하네스 픽스처(MOM-0877).
--
-- 레거시 momens-api 000001_init.sql 과 신규 서버의 users/workspace 마이그레이션은 이 세 테이블에
-- 대해 동일한 DDL 입니다. 그래서 같은 INSERT 가 양쪽 DB 에서 그대로 돕니다.
--
-- id 와 시각을 고정하는 이유: 값까지 문자 그대로 같아야 diff 가 곧 계약 차이가 됩니다. 고정하지
-- 않으면 매 실행마다 UUID·타임스탬프가 달라 정규화가 필요하고, 정규화는 진짜 차이를 함께 지웁니다.
--
-- ⚠️  파괴적입니다. 아래 TRUNCATE 는 CASCADE 로 projects, tasks, user_identities 등 users 를
--     참조하는 모든 테이블까지 비웁니다. run.sh 는 이 파일을 compose 가 띄운 일회용 컨테이너에만
--     적용하므로 안전하지만, psql 명령을 그대로 복사해 로컬 개발 DB(docker-compose.yml 의
--     momens-postgres-data 볼륨)에 돌리면 그 데이터가 사라집니다. 대상 DB 를 반드시 확인하세요.

BEGIN;

-- TRUNCATE ... CASCADE 는 딸려 비워지는 테이블마다 NOTICE 를 냅니다. write 케이스마다 이 파일을
-- 다시 적용하므로(MOM-0882) 그대로 두면 NOTICE 가 차등 비교 출력을 덮습니다.
SET LOCAL client_min_messages TO WARNING;

TRUNCATE workspace_members, workspaces, users CASCADE;
TRUNCATE source_refs CASCADE;

INSERT INTO users (id, email, name, avatar_url, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000001', 'owner@momens.works',    '홍길동', 'https://cdn.momens.works/avatars/owner.png', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'),
  ('00000000-0000-4000-8000-000000000002', 'member@momens.works',   '김철수', NULL,                                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'),
  ('00000000-0000-4000-8000-000000000003', 'stranger@momens.works', '박영희', NULL,                                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'),
  ('00000000-0000-4000-8000-000000000004', 'nobody@momens.works',   '이민수', NULL,                                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z');

-- created_at 을 서로 다르게 둬 목록 정렬(내림차순)을 검증합니다.
-- alpha 는 description 이 NULL 이라 레거시 omitempty 생략 동작을 드러냅니다.
INSERT INTO workspaces (id, name, slug, description, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000011', 'Alpha', 'ws-alpha', NULL,                 '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'),
  ('00000000-0000-4000-8000-000000000012', 'Beta',  'ws-beta',  '제품팀 워크스페이스', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
  ('00000000-0000-4000-8000-000000000013', 'Gamma', 'ws-gamma', '외부 워크스페이스',   '2026-03-01T00:00:00Z', '2026-03-01T00:00:00Z');

-- owner 는 alpha·beta 의 멤버이고 gamma 는 아닙니다(403 경로).
-- nobody 는 어디에도 속하지 않습니다(빈 목록 경로).
INSERT INTO workspace_members (workspace_id, user_id, role, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000001', 'owner',  '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'),
  ('00000000-0000-4000-8000-000000000012', '00000000-0000-4000-8000-000000000001', 'member', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
  ('00000000-0000-4000-8000-000000000012', '00000000-0000-4000-8000-000000000002', 'owner',  '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
  ('00000000-0000-4000-8000-000000000013', '00000000-0000-4000-8000-000000000003', 'owner',  '2026-03-01T00:00:00Z', '2026-03-01T00:00:00Z'),
  ('00000000-0000-4000-8000-000000000012', '00000000-0000-4000-8000-000000000003', 'admin',  '2026-02-15T00:00:00Z', '2026-02-15T00:00:00Z');

-- H023은 레거시의 projects.progress와 DATE 직렬화가 신규 계약과 의도적으로 다른지 golden으로 고정합니다.
INSERT INTO projects (id, workspace_id, label, name, owner_id, target_date, progress, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000021', '00000000-0000-4000-8000-000000000012', 'PRJ-0001', 'Beta 프로젝트', '00000000-0000-4000-8000-000000000002', '2026-07-31', 40, '2026-02-02T00:00:00Z', '2026-02-02T00:00:00Z');

-- milestone은 owner 행을 두지 않아 snapshot의 빈 owner_user_ids 직렬화 차이를 함께 대조합니다.
INSERT INTO milestones (id, project_id, name, progress, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000022', '00000000-0000-4000-8000-000000000021', 'Beta 마일스톤', 40, '2026-02-03T00:00:00Z', '2026-02-03T00:00:00Z');

-- H040은 워크스페이스별 연결 목록과 생성 시각 내림차순 정렬을 대조합니다.
INSERT INTO source_connections (id, workspace_id, source_type, status, external_workspace_id, external_workspace_name, connected_by_user_id, connected_at, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000031', '00000000-0000-4000-8000-000000000012', 'GITHUB', 'ACTIVE', 'momens-org', 'Momens', '00000000-0000-4000-8000-000000000001', '2026-04-01T00:00:00Z', '2026-04-01T00:00:00Z', '2026-04-01T00:00:00Z'),
  ('00000000-0000-4000-8000-000000000032', '00000000-0000-4000-8000-000000000012', 'SLACK',  'ACTIVE', 'T-momens',   'momens',  '00000000-0000-4000-8000-000000000001', '2026-04-02T00:00:00Z', '2026-04-02T00:00:00Z', '2026-04-02T00:00:00Z');

-- H096은 검증 후 응답 필드 구성과 verified_* 갱신을 대조합니다.
INSERT INTO source_refs (id, workspace_id, source_type, source_object_type, source_object_id, source_url, title, snippet, text, visibility, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000041', '00000000-0000-4000-8000-000000000012', 'figma', 'FILE_COMMENT', 'obj-1', 'https://figma.com/file/abc', '권한 요청 화면 v2', '설명 문구 변경', '수집한 원문 전체', 'WORKSPACE', '2026-04-03T00:00:00Z', '2026-04-03T00:00:00Z'),
  ('00000000-0000-4000-8000-000000000042', '00000000-0000-4000-8000-000000000013', 'figma', 'FILE_COMMENT', 'obj-2', 'https://figma.com/file/def', '다른 워크스페이스 화면', '다른 워크스페이스 설명', '다른 워크스페이스 원문', 'WORKSPACE', '2026-04-04T00:00:00Z', '2026-04-04T00:00:00Z');

COMMIT;
