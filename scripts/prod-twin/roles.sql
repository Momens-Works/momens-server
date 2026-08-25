-- prod 쌍둥이의 role 형상 (MOM-0909).
--
-- prod(Supabase 프로젝트 `Momens`)에서 읽기 전용으로 실측한 형상을 재현한다. 실측 근거는
-- MOM-0909 코멘트(2026-08-25)이고, 재현 대상은 다음 넷이다.
--
--   anon / authenticated / service_role  Supabase 가 Data API 용으로 두는 role. NOLOGIN 이고
--                                        PostgREST 가 세션 안에서 SET ROLE 로 갈아탄다.
--   momens_server                        보안상 신설한 서버 전용 LOGIN role.
--
-- 재현하지 않는 것: RLS. prod 는 전 테이블 `relrowsecurity = false` 이고 Postgres 기본값이
-- 그러므로 아무것도 하지 않는 것이 곧 재현이다.
--
-- 이 파일은 `postgres` 로 실행한다. prod 의 기존 테이블 소유자가 전부 `postgres` 이므로
-- ALTER DEFAULT PRIVILEGES 의 FOR ROLE 도 `postgres` 여야 실제와 같아진다.

-- --- Supabase Data API role ---------------------------------------------------
-- NOINHERIT 는 Supabase 의 실제 정의를 따른 것이다. PostgREST 가 SET ROLE 로 명시적으로
-- 갈아타므로 상속이 필요 없다.
CREATE ROLE anon NOLOGIN NOINHERIT;
CREATE ROLE authenticated NOLOGIN NOINHERIT;
CREATE ROLE service_role NOLOGIN NOINHERIT BYPASSRLS;

GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated, service_role;

-- 이 두 줄이 MOM-0925 가 관측한 "공개 anon 키로 전 테이블 읽기·삭제"의 기전이다. 앞으로
-- **`postgres` 가 만드는** 테이블에 자동으로 권한이 붙는다. 부트스트랩이 만드는 12 개 테이블의
-- 소유자는 `postgres` 가 아니라 `momens_server` 이므로 여기에 걸리지 않는다 — 그 차이가
-- 쌍둥이에서 실측할 대상이다.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    GRANT ALL ON TABLES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;

-- --- 운영 창구 role ---------------------------------------------------------
-- Supabase 의 `postgres` 는 **superuser 가 아니다.** SQL Editor 가 그 세션이고, public 의
-- 기존 테이블 소유자도 그것이다. 이 차이가 중요한 이유는 `ALTER TABLE ... OWNER TO` 때문이다 —
-- superuser 는 아무 role 로나 소유권을 넘길 수 있지만 비-superuser 는 **대상 role 로 SET ROLE 할
-- 수 있어야** 한다. 쌍둥이의 부트스트랩 컨테이너 `postgres` 는 superuser 라 이 검사를 통째로
-- 건너뛰므로, 창구를 따로 만들어 그 구분을 재현한다.
--
-- 이 role 이 곧 "운영자가 SQL Editor 에서 실행한다"의 로컬 대역이다.
CREATE ROLE sb_postgres NOSUPERUSER CREATEROLE CREATEDB LOGIN PASSWORD 'sb_postgres';
GRANT USAGE, CREATE ON SCHEMA public TO sb_postgres;

-- prod 의 기존 테이블 소유자는 전부 `postgres` 다(실측). 쌍둥이에서는 레거시 마이그레이션을
-- 부트스트랩 superuser 로 돌렸으므로 소유권을 창구 role 로 옮겨 형상을 맞춘다. 확장(uuid-ossp,
-- vector)은 Supabase 에서도 별도 role 이 소유하므로 옮기지 않는다.
DO $$
DECLARE t record;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER TABLE public.%I OWNER TO sb_postgres', t.tablename);
    END LOOP;
END $$;

-- Data API 기본 권한은 창구 role 이 만드는 테이블에 붙어야 한다. prod 의 ALTER DEFAULT
-- PRIVILEGES 도 `FOR ROLE postgres` 이므로 여기서는 sb_postgres 가 그 자리다.
ALTER DEFAULT PRIVILEGES FOR ROLE sb_postgres IN SCHEMA public
    GRANT ALL ON TABLES TO anon, authenticated, service_role;

-- --- 서버 전용 role -----------------------------------------------------------
-- superuser 가 아니고 어떤 테이블의 소유자도 아니다. 이 두 가지가 지금까지의 리허설이
-- 재현하지 못한 부분이다(설계 문서 8절 "아직 리허설로 닫지 못한 것").
--
-- 창구(sb_postgres)가 이 role 로 SET ROLE 할 수 있는지는 **여기서 주지 않는다.** PG16+ 에서
-- CREATEROLE role 이 만든 role 의 자동 멤버십은 `admin=true, inherit=false, set=false` 라
-- 소유권 이전에 쓸 수 없다. 그 GRANT 는 관리자 조치이고, 있고 없고를 시나리오가 가른다.
CREATE ROLE momens_server LOGIN PASSWORD 'momens_server';

-- prod 실측: `sch_create = true`. 관리자 조치 이전부터 있었다.
GRANT USAGE, CREATE ON SCHEMA public TO momens_server;

-- prod 실측: 관리자가 레거시 테이블에 DML GRANT 를 마쳤다. 대상은 서버 엔티티가 매핑하면서
-- 레거시가 이미 만든 테이블인데, 그 교집합 19 개 중 **`tasks` 는 의도적으로 뺐다.** 소유권을
-- 넘기면 소유자로서 DML 이 따라온다는 판단이었고, 관리자가 실행한 18 개는 지시대로다.
--
-- 그 판단이 만든 결합을 쌍둥이가 재는 것이 `ownership-reverted` 시나리오다. 여기서는 prod 형상을
-- 그대로 재현해야 하므로 **`tasks` 를 빼 둔다.**
--
-- 소유권도 주지 않는다. `ALTER TABLE` 은 GRANT 대상이 아니라 소유자 권한이라, 여기서
-- 멈추면 부트스트랩이 `tasks` 에서 죽는 것이 정상이다.
GRANT SELECT, INSERT, UPDATE, DELETE ON
    blockers, confirmed_memories, entity_relations, memory_candidates,
    milestone_owners, milestones, project_owners, projects, refresh_tokens,
    source_connections, source_credentials, source_refs, task_updates,
    users, workspace_invitations, workspace_label_sequences, workspace_members,
    workspaces
TO momens_server;
