-- prod-schema: mirror
-- 웹 task read(H053/H060/H095)가 레거시 tasks의 milestone_id·due_date를 읽기 위한 local/test 미러 보강입니다.
ALTER TABLE tasks
    ADD COLUMN milestone_id UUID REFERENCES milestones(id) ON DELETE SET NULL,
    ADD COLUMN due_date DATE;
