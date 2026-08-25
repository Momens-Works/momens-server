-- prod 쌍둥이의 Supabase 고유 형상 (MOM-0909).
--
-- 레거시 마이그레이션만으로는 재현되지 않는, Supabase 가 프로젝트에 미리 깔아 두는 것들이다.
-- 전부 prod 에서 읽기 전용으로 실측한 값을 옮긴 것이다(2026-08-25).
--
-- 이 파일이 없으면 쌍둥이는 확장이 public 에 있고 event trigger 가 없는 DB 가 되는데, 그것은
-- prod 가 아니다. 실제로 이 차이가 블로커 하나를 가리고 있었다.

-- --- 확장 스키마 ---------------------------------------------------------------
-- prod 실측:
--   pg_stat_statements  extensions
--   pgcrypto            extensions
--   uuid-ossp           extensions     ← 이것이 문제의 지점
--   vector              public
--   supabase_vault      vault
--   plpgsql             pg_catalog
--
-- Supabase 가 uuid-ossp 를 기본 활성화해 두었으므로 레거시 000001_init.sql 의
-- `CREATE EXTENSION IF NOT EXISTS "uuid-ossp"` 는 prod 에서 no-op 이었다. 쌍둥이에서는 그
-- 구문이 실제로 public 에 만들었으므로 옮겨서 형상을 맞춘다.
--
-- 기존 테이블의 `DEFAULT uuid_generate_v4()` 는 생성 시점에 OID 로 굳어 있어 옮겨도 깨지지
-- 않는다. prod 도 같다 — 그래서 런타임에는 아무 신호가 없고 DDL 시점에만 터진다.
CREATE SCHEMA IF NOT EXISTS extensions;
ALTER EXTENSION "uuid-ossp" SET SCHEMA extensions;

-- Supabase 는 자기 role 들에만 USAGE 를 준다. **momens_server 는 여기 없다** — 커스텀 role 이라
-- 어디에도 자동으로 끼지 않는다. search_path 에 넣는 것만으로 충분한지, USAGE 도 필요한지는
-- 쌍둥이가 판정할 몫이다.
GRANT USAGE ON SCHEMA extensions TO sb_postgres, anon, authenticated, service_role;

-- --- 창구 role 의 search_path --------------------------------------------------
-- prod 실측: postgres 는 `"$user", public, extensions`.
-- momens_server 는 pg_db_role_setting 에 항목이 없어 서버 기본값 `"$user", public` 이다.
-- **extensions 가 없다.** 이 비대칭이 이 파일의 요점이다.
ALTER ROLE sb_postgres SET search_path = "$user", public, extensions;

-- --- event trigger -------------------------------------------------------------
-- prod 실측: supabase_admin 소유로 6 개가 켜져 있다.
--
--   ddl_command_end : pgrst_ddl_watch, issue_pg_cron_access,
--                     issue_pg_graphql_access, issue_pg_net_access
--   sql_drop        : pgrst_drop_watch, issue_graphql_placeholder
--
-- 아래는 Supabase 의 실제 함수를 **본뜬 것**이지 복사가 아니다. 재현의 목적은 함수의 정확한
-- 내용이 아니라 "우리 트랜잭션 안에서 남의 코드가 매 DDL 마다 돈다"는 조건이다. 특히 이 함수를
-- 실행하는 것은 supabase_admin 이 아니라 **DDL 을 실행한 role(momens_server)** 이다.
--
-- sql_drop 쪽은 실행 집합에 DROP 이 하나도 없어(설계 7절 원칙) 발화하지 않지만, 그 사실 자체를
-- 확인하기 위해 함께 만든다.

CREATE OR REPLACE FUNCTION extensions.pgrst_ddl_watch() RETURNS event_trigger
LANGUAGE plpgsql AS $$
DECLARE cmd record;
BEGIN
    FOR cmd IN SELECT * FROM pg_event_trigger_ddl_commands() LOOP
        IF cmd.command_tag IN ('CREATE TABLE', 'ALTER TABLE', 'CREATE INDEX',
                               'CREATE SCHEMA', 'CREATE FUNCTION', 'CREATE VIEW')
           AND cmd.schema_name IS DISTINCT FROM 'pg_temp' THEN
            NOTIFY pgrst, 'reload schema';
        END IF;
    END LOOP;
END $$;

CREATE OR REPLACE FUNCTION extensions.grant_pg_cron_access() RETURNS event_trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_event_trigger_ddl_commands()
                WHERE command_tag = 'CREATE EXTENSION' AND object_identity = 'pg_cron') THEN
        RAISE NOTICE 'pg_cron access would be granted here';
    END IF;
END $$;

CREATE OR REPLACE FUNCTION extensions.grant_pg_net_access() RETURNS event_trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_event_trigger_ddl_commands()
                WHERE command_tag = 'CREATE EXTENSION' AND object_identity = 'pg_net') THEN
        RAISE NOTICE 'pg_net access would be granted here';
    END IF;
END $$;

-- 이 함수는 pg_event_trigger_ddl_commands() 를 pg_proc 에 조인한다. CREATE TABLE 이벤트의
-- objid 는 테이블 OID 라 조인이 비고, 결과가 NULL 이 되어 IF 가 거짓이 된다. 그 경로를 그대로
-- 재현한다 — 우리 DDL 이 타는 것이 정확히 이 경로다.
CREATE OR REPLACE FUNCTION extensions.grant_pg_graphql_access() RETURNS event_trigger
LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE func_is_graphql_resolve bool;
BEGIN
    func_is_graphql_resolve = (
        SELECT p.proname = 'resolve'
          FROM pg_event_trigger_ddl_commands() AS ev
          JOIN pg_catalog.pg_proc AS p ON ev.objid = p.oid);
    IF func_is_graphql_resolve THEN
        RAISE NOTICE 'graphql access would be granted here';
    END IF;
END $$;

CREATE OR REPLACE FUNCTION extensions.set_graphql_placeholder() RETURNS event_trigger
LANGUAGE plpgsql AS $$
DECLARE dropped record;
BEGIN
    FOR dropped IN SELECT * FROM pg_event_trigger_dropped_objects() LOOP
        IF dropped.object_type = 'extension' AND dropped.object_name = 'pg_graphql' THEN
            RAISE NOTICE 'graphql placeholder would be set here';
        END IF;
    END LOOP;
END $$;

CREATE OR REPLACE FUNCTION extensions.pgrst_drop_watch() RETURNS event_trigger
LANGUAGE plpgsql AS $$
DECLARE obj record;
BEGIN
    FOR obj IN SELECT * FROM pg_event_trigger_dropped_objects() LOOP
        IF obj.object_type IN ('table', 'view', 'function', 'schema')
           AND obj.is_temporary IS FALSE THEN
            NOTIFY pgrst, 'reload schema';
        END IF;
    END LOOP;
END $$;

CREATE EVENT TRIGGER pgrst_ddl_watch ON ddl_command_end
    EXECUTE FUNCTION extensions.pgrst_ddl_watch();
CREATE EVENT TRIGGER issue_pg_cron_access ON ddl_command_end
    EXECUTE FUNCTION extensions.grant_pg_cron_access();
CREATE EVENT TRIGGER issue_pg_net_access ON ddl_command_end
    EXECUTE FUNCTION extensions.grant_pg_net_access();
CREATE EVENT TRIGGER issue_pg_graphql_access ON ddl_command_end
    EXECUTE FUNCTION extensions.grant_pg_graphql_access();
CREATE EVENT TRIGGER pgrst_drop_watch ON sql_drop
    EXECUTE FUNCTION extensions.pgrst_drop_watch();
CREATE EVENT TRIGGER issue_graphql_placeholder ON sql_drop
    EXECUTE FUNCTION extensions.set_graphql_placeholder();
