# 영속성 (DB · JPA · Flyway)

## DB

- PostgreSQL만 지원합니다. 초기 DB 접근은 JPA. QueryDSL은 필요 시 추가합니다([P8](../../DECISIONS-PENDING.md)).
- DB 통합 테스트는 PostgreSQL Testcontainers를 사용합니다(H2 미사용).

## 엔티티

- `@Getter` 허용, `@Setter` 지양, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 필수,
  `@Builder` 허용.
- 생성자·정적 팩토리·`@Builder`는 생성 의도와 필드 수에 따라 선택합니다.
- 자기 상태·불변식 로직은 엔티티에 둡니다([Spring > 레이어 책임](../code/spring.md#레이어-책임)).
- `equals`/`hashCode`에서 연관 필드는 제외합니다.

## 마이그레이션 (Flyway)

- Flyway로 관리하고, 위치는 `classpath:db/migration`입니다.
- 이미 적용된 마이그레이션은 수정하지 않습니다(변경은 새 마이그레이션으로).
- 파일명은 순차 버전 + snake_case 설명: `V<n>__<설명>.sql`
  (예: `V1__create_user.sql`, `V2__add_user_index.sql`).
