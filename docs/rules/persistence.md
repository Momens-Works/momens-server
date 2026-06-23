# 데이터

영속성(DB · JPA · Flyway)과 시간 · 식별자 규칙입니다.

## 영속성 (DB · JPA · Flyway)

### DB

- PostgreSQL만 지원합니다. 초기 DB 접근은 JPA. QueryDSL은 필요 시 추가합니다([P8](../pending-decisions.md)).
- DB 통합 테스트는 PostgreSQL Testcontainers를 사용합니다(H2 미사용).

### 엔티티

- `@Getter` 허용, `@Setter` 지양, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 필수,
  `@Builder` 허용.
- 생성자·정적 팩토리·`@Builder`는 생성 의도와 필드 수에 따라 선택합니다.
- 자기 상태·불변식 로직은 엔티티에 둡니다([코드 > Spring > 레이어 책임](code-conventions.md#레이어-책임)).
- `equals`/`hashCode`에서 연관 필드는 제외합니다.

### 마이그레이션 (Flyway)

- Flyway로 관리하고, 위치는 `classpath:db/migration`입니다.
- 이미 적용된 마이그레이션은 수정하지 않습니다(변경은 새 마이그레이션으로).
- 각 Gradle 모듈이 자기 마이그레이션을 소유하고(`<module>/src/main/resources/db/migration`),
  `app` 클래스패스에서 하나의 Flyway 이력으로 병합됩니다. 공유 확장(`uuid-ossp` 등)은
  `common`이 소유합니다.
- 파일명은 타임스탬프 버전 + snake_case 설명: `V<yyyyMMddHHmmss>__<설명>.sql`
  (예: `V20260624090100__create_user.sql`). 타임스탬프 버전으로 모듈 간 버전 충돌을 피하고
  병합 시 실행 순서를 보장합니다.
- Spring Modulith event publication registry의 `event_publication` 테이블도 Flyway로
  관리합니다. 첫 application event 도입 시 함께 추가합니다([아키텍처](architecture.md), [ADR-0001](../adr/0001-modular-monolith-rules.md)).

## 시간 · 식별자

레거시 `momens-api` 스키마와 호환을 유지합니다.

### 식별자

- PK는 UUID v4 (Java `UUID` ↔ Postgres `uuid`).
- UUID는 앱 측에서 생성합니다(엔티티 생성 시 `UUID.randomUUID()`). 스키마의
  `DEFAULT uuid_generate_v4()`는 안전망으로 둘 수 있습니다.
- 조인 테이블은 복합 PK를 사용할 수 있습니다.

### 시간

- 시간 타입은 `timestamptz` ↔ Java `Instant`. 저장·처리 모두 UTC 기준이며, 표시(타임존
  변환)는 클라이언트가 담당합니다.
- 날짜만 필요한 값은 `DATE` ↔ `LocalDate`.

### 감사 필드

- 모든 테이블에 `created_at`/`updated_at`(`NOT NULL`)을 둡니다.
- 값은 JPA Auditing(`@CreatedDate`/`@LastModifiedDate`)으로 채웁니다. 스키마의
  `NOT NULL DEFAULT NOW()`는 안전망으로 둡니다.

### 소프트 삭제

- `deleted_at`(`timestamptz`, nullable)로 소프트 삭제를 표현합니다. 적용 범위는
  레거시 기준으로 엔티티별로 판단합니다.
