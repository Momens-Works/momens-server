-- prod-schema: mirror
-- task context 웹 응답이 읽는 source_refs 컬럼을 local/test 미러에 보강합니다.
ALTER TABLE source_refs
    ADD COLUMN source_object_type TEXT NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN source_object_id TEXT NOT NULL DEFAULT '',
    ADD COLUMN author_name TEXT,
    ADD COLUMN author_email TEXT,
    ADD COLUMN visibility TEXT NOT NULL DEFAULT 'WORKSPACE',
    ADD COLUMN permission_key TEXT,
    ADD COLUMN verified_by_user_id UUID,
    ADD COLUMN verified_at TIMESTAMPTZ,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
