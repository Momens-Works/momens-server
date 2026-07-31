-- task 출처 추적(CO-6). 사람이 직접 만든 태스크와 Signal을 수용해 만든 태스크를 구분한다.
-- 기존 행(local/test 데이터뿐)은 전부 사람이 만든 태스크라 manual/NULL로 채운다.
ALTER TABLE tasks
    ADD COLUMN origin_type TEXT NOT NULL DEFAULT 'manual'
        CHECK (origin_type IN ('manual', 'signal')),
    ADD COLUMN origin_signal_id UUID,
    ADD CONSTRAINT tasks_origin_signal_check
        CHECK ((origin_type = 'signal') = (origin_signal_id IS NOT NULL));
