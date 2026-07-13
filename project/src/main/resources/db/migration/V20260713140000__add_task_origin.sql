-- tasks에 출처(origin)를 추가한다(ADR-0008 CO-6). 사람이 직접 만든 태스크(manual)와 Signal/Minsu 제안을
-- 수용해 만든 태스크(signal)를 의미로 구분하고, signal 출처는 origin_signal_id로 원본 Signal을 보존한다.
--
-- 기존 행(local/test 데이터뿐)은 전부 사람이 만든 것이라 DEFAULT 'manual'로 backfill된다. DEFAULT는 앱이
-- 항상 출처를 넘기는 것과 별개로 두는 안전망이다(status/priority와 같은 방식). CHECK로 signal 출처일 때만
-- origin_signal_id가 있고, manual일 때는 없다는 불변식을 강제한다.
ALTER TABLE tasks
    ADD COLUMN origin_type TEXT NOT NULL DEFAULT 'manual'
        CHECK (origin_type IN ('manual', 'signal')),
    ADD COLUMN origin_signal_id UUID,
    ADD CONSTRAINT tasks_origin_signal_check
        CHECK ((origin_type = 'signal') = (origin_signal_id IS NOT NULL));
