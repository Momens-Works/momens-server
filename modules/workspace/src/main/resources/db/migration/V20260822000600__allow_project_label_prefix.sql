-- prod-schema: mirror
-- 레거시 `momens-api`의 `000017_project_label.sql`이 `workspace_label_sequences`의 `CHECK` 제약에
-- 추가한 `PRJ` 접두사를 local과 test 환경에도 반영합니다. 운영 스키마에는 이미 적용되어 있으므로
-- 별도로 반영할 필요가 없습니다.
--
-- 제약 이름과 정의는 레거시와 동일하게 유지합니다. 이 서버가 사용하는 테이블의 제약은 레거시보다
-- 적거나 많아서는 안 됩니다(`docs/rules/persistence.md`). 제약이 더 적으면 운영 환경에서 거부될
-- 값이 테스트를 통과하고, 더 많으면 운영 환경에 실제로 존재하는 상태를 픽스처에서 재현할 수
-- 없습니다.
--
-- `000017`에서 함께 추가한 `assign_project_label` 함수와 `trg_projects_label` 트리거, 기존 프로젝트
-- 행에 라벨을 채우는 작업은 옮기지 않습니다. 운영 환경에서 대신 채워 주던 값을 이 서버에서는
-- `LabelAllocator`가 직접 발급해 `INSERT`합니다. 여기에 트리거까지 추가하면 해당 발급 로직이 local
-- 환경에서만 우회됩니다.
--
-- 라벨 발급 경로 구현은 MOM-0907에서 담당합니다. `V20260819090000__add_project_web_columns.sql`의
-- 주석에는 MOM-0866으로 적혀 있지만, 프로젝트 생성과 워크스페이스 생성에 공통으로 필요한 선행
-- 작업으로 분리되면서 담당 작업이 변경되었습니다. 해당 마이그레이션은 이미 적용되어 주석만 수정해도
-- Flyway checksum이 달라지므로, 변경된 작업 번호는 이 파일에 기록합니다.
ALTER TABLE workspace_label_sequences
    DROP CONSTRAINT workspace_label_sequences_label_prefix_check,
    ADD CONSTRAINT workspace_label_sequences_label_prefix_check
        CHECK (label_prefix IN ('SUG', 'MEM', 'MOM', 'PRJ'));
