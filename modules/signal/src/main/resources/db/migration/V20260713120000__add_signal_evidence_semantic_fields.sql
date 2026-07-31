-- local/test·dev signal_evidence 미러에 ADR-0011 의미 필드(대상·변화·영향)를 추가한다.
--
-- signal_evidence는 worker가 Signal 생성 시 함께 적재하며(ADR-0007), ADR-0011로 근거마다의 대상(target)·
-- 변화(change)·영향(impact) 의미 값을 이 테이블이 소유한다. 세 값은 생산 단계(worker 또는 같은 backing
-- 계약을 따르는 fixture)에서 공백 포함 30자 이하로 만들고, api-server는 자르거나 생성하지 않고 그대로 읽는다.
-- 30자 계약은 CHECK로 미러에서 강제해, fixture가 계약을 어기면 삽입이 실패한다(NULL은 CHECK를 통과한다).
--
-- prod 공유 DB의 동일 컬럼은 레거시 momens-api 마이그레이션이 소유하며(ADR-0011, 아직 미반영), 이 파일은
-- local/test와 dev(application-dev.yml Flyway on)에서만 실행되는 미러다. 이미 행이 있어도 적용되도록
-- nullable로 추가한다. change는 예약 식별자와의 혼동을 피해 따옴표로 감싼다.
ALTER TABLE signal_evidence
    ADD COLUMN target TEXT CHECK (char_length(target) <= 30),
    ADD COLUMN "change" TEXT CHECK (char_length("change") <= 30),
    ADD COLUMN impact TEXT CHECK (char_length(impact) <= 30);
