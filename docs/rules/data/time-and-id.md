# 시간 · 식별자

레거시 `momens-api` 스키마와 호환을 유지합니다.

## 식별자

- PK는 UUID v4 (Java `UUID` ↔ Postgres `uuid`).
- UUID는 앱 측에서 생성합니다(엔티티 생성 시 `UUID.randomUUID()`). 스키마의
  `DEFAULT uuid_generate_v4()`는 안전망으로 둘 수 있습니다.
- 조인 테이블은 복합 PK를 사용할 수 있습니다.

## 시간

- 시간 타입은 `timestamptz` ↔ Java `Instant`. 저장·처리 모두 UTC 기준이며, 표시(타임존
  변환)는 클라이언트가 담당합니다.
- 날짜만 필요한 값은 `DATE` ↔ `LocalDate`.

## 감사 필드

- 모든 테이블에 `created_at`/`updated_at`(`NOT NULL`)을 둡니다.
- 값은 JPA Auditing(`@CreatedDate`/`@LastModifiedDate`)으로 채웁니다. 스키마의
  `NOT NULL DEFAULT NOW()`는 안전망으로 둡니다.

## 소프트 삭제

- `deleted_at`(`timestamptz`, nullable)로 소프트 삭제를 표현합니다. 적용 범위는
  레거시 기준으로 엔티티별로 판단합니다.
