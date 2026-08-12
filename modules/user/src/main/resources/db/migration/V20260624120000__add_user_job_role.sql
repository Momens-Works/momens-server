-- prod-schema: mirror
-- 레거시 momens-api 의 users.job_role 컬럼과 호환됩니다. nullable(미설정 시 null).
ALTER TABLE users ADD COLUMN job_role TEXT;
