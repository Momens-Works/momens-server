-- UUID PK 기본값(uuid_generate_v4)의 안전망용 확장. 앱이 UUID를 생성하지만,
-- 레거시 스키마와 동일하게 DB 측 DEFAULT도 함께 둡니다. 여러 모듈이 공유하므로
-- 공유 인프라 모듈(common)이 소유합니다.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
