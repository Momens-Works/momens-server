# 데이터

영속성(DB · JPA · Flyway)과 시간 · 식별자 규칙입니다.

## 영속성 (DB · JPA · Flyway)

### DB

- PostgreSQL만 지원합니다. 초기 DB 접근은 JPA. QueryDSL은 필요 시 추가합니다.
- DB 통합 테스트는 PostgreSQL Testcontainers를 사용합니다(H2 미사용).

### 엔티티

- `@Getter` 허용, `@Setter` 지양, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 필수,
  `@Builder` 허용.
- 생성자·정적 팩토리·`@Builder`는 생성 의도와 필드 수에 따라 선택합니다.
- 자기 상태·불변식 로직은 엔티티에 둡니다([코드 > Spring > 레이어 책임](code-conventions.md#레이어-책임)).
- `equals`/`hashCode`에서 연관 필드는 제외합니다.
- `BaseEntity` 상속은 엔티티가 `id`·`created_at`·`updated_at`을 모두 매핑할 때입니다. 셋 중 하나라도
  매핑하지 않으면 `@Id`를 직접 둡니다. 읽기 전용인지 여부는 기준이 아닙니다. 쓰기 경로가 없는 엔티티는
  `@Immutable`로 막되, 감사 3종을 매핑한다면 상속합니다.

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
- 운영(`prod`)에서는 레거시 `momens-api`와 **공유 DB를 함께 쓰는 전환기** 동안 Flyway를 끄고
  (`spring.flyway.enabled=false`) `ddl-auto=validate`로 매핑만 검증합니다(conformer). 공유 운영
  스키마는 레거시가 단일 소유하며, 새 서버는 운영에서 스키마를 만들거나 바꾸지 않습니다. 진짜
  신규 테이블은 별도 레거시 마이그레이션으로 추가합니다. `local`/`test`는 새 서버 Flyway가 그대로
  소유합니다(별도 DB라 충돌 없음).

### prod 반영 헤더

위 규칙 때문에 이 서버의 마이그레이션은 대부분 **prod 반영 의무를 하나씩 만듭니다.** 반영되지
않으면 매핑 검증에 실패해 애플리케이션이 기동하지 않으므로, 각 마이그레이션 **첫 줄**에 그 상태를
적습니다. 이 헤더가 정본이고 [prod 운영 준비 대장](../prod-schema-ledger.md)의 스키마 구간은
`scripts/prod-schema-ledger.sh --write`가 헤더에서 생성합니다.

| 헤더 | 의미 |
| --- | --- |
| `-- prod-schema: mirror` | 레거시가 이미 소유한 스키마입니다. 이 파일은 `local`/`test` 미러이고 prod 반영 의무가 없습니다 |
| `-- prod-schema: required MOM-<번호>` | prod 반영이 필요하고 아직 반영 PR이 없습니다. 반영을 추적하는 작업 라벨을 적습니다 |
| `-- prod-schema: pending <저장소>#<PR번호>` | 반영 PR이 열려 있고 아직 prod에 적용되지 않았습니다 |
| `-- prod-schema: applied <저장소>#<PR번호>` | prod 적용이 끝났습니다 |

저장소는 `momens-api` 또는 `momens-worker`입니다. 레거시가 소유한 스키마는 `momens-api`가
반영하지만, worker가 생산하는 테이블(`signals` 계열, ADR-0007)의 반영 위치는 아직 확정되지
않았습니다. 확정 전에는 `required`로 두고 추적 작업이 소유합니다.

- 상태가 바뀌면 헤더를 고치고 `--write`로 대장을 다시 생성해 함께 커밋합니다.
- `pending`은 **선택 상태**입니다. 게이트는 `required`와 똑같이 막으므로 거쳐 가도 결과가 바뀌지
  않고, 헤더를 한 번 더 고치는 만큼 checksum 비용만 늘어납니다. `required`에서 `applied`로 바로
  가는 것이 기본이고, 반영 PR 머지와 prod 적용 사이가 벌어져 "반영 PR은 있는데 아직 적용 전"을
  드러내야 할 때만 씁니다.
- `pr-format` CI가 헤더 누락과 대장 최신 여부를 검사하고, `main`을 대상으로 하는 PR에서는
  `required`·`pending`이 하나라도 남아 있으면 실패합니다. 운영 배포 트리거가 `main` push라
  검사 대상을 릴리즈 PR로 좁히면 hotfix 같은 경로가 게이트를 우회합니다. 미반영 스키마가 prod에
  나가는 것을 릴리스 직전이 아니라 PR 시점에 막기 위한 것입니다.
- 판단이 서지 않으면 `required`로 두고 확인합니다. 잘못 `mirror`로 두면 게이트가 조용히
  통과시킵니다.

`required`는 **"이 파일을 그대로 옮긴다"가 아니라 "이 파일이 만드는 객체 중 prod에 없는 것이
있다"**는 뜻입니다. 헤더는 파일마다 하나지만 마이그레이션 하나가 여러 객체를 건드릴 수 있고, 그중
일부는 레거시에 이미 있을 수 있습니다. 예를 들어 `V20260707120000__add_task_detail_and_checklist.sql`은
`task_checklist_items`를 새로 만들지만 같은 파일의 `tasks.description`·`assignee_id`는 레거시
`000001_init.sql`에 이미 있습니다. 반영 범위는 **반영 시점에 객체 단위로 대조해** 정합니다.

대장이 추적하는 단위는 **이 저장소의 마이그레이션 파일**입니다. 서버 파일이 없는 prod 스키마
의무(예: 레거시 전용 제약 제거)는 헤더를 달 자리가 없어 대장에 들어가지 않고 작업 티켓이
추적합니다. 특히 "서버 코드 배포가 먼저, 제약 변경이 나중"인 역방향 순서를 요구하는 의무를
`required`로 넣으면 릴리스 게이트가 1단계 배포부터 막아 교착이 생깁니다.

이 헤더는 "이미 적용된 마이그레이션은 수정하지 않습니다" 규칙의 **한정된 예외**입니다. SQL 동작은
바꾸지 않지만 Flyway checksum은 주석까지 포함해 계산하므로, 헤더를 달거나 상태를 바꾸면 이미
적용된 DB에서 checksum이 어긋납니다. Flyway가 켜지는 `local`·`dev`는 DB를 다시 만들거나
`flyway repair`가 필요합니다([로컬 개발](../local-development.md#마이그레이션-checksum-불일치)).
`test`는 매번 새 DB라, `prod`는 Flyway가 꺼져 있어 무관합니다. 상태를 파일 밖에 두면 이 비용은
사라지지만 기록이 코드에서 떨어져 나가 강제할 지점이 없어지므로, 비용을 아는 채로 헤더를
정본으로 둡니다.

### 레거시 소유 테이블 미러

`mirror` 헤더가 붙은 마이그레이션은 **레거시 `momens-api`가 소유한 테이블의 `local`/`test` 전용
복제본**입니다. prod에서는 공유 DB에 실물이 있고 이 서버의 Flyway가 꺼져 있으므로, 미러가 운영
스키마가 되는 일은 없습니다.

그래서 판단 기준은 "얼마나 똑같이 베끼느냐"가 아닙니다. 공유 DB 전환기의 실제 위험은 **local에서는
통과하는데 prod에서 깨지는 것**이고, 미러의 존재 이유는 그 간극을 막는 데 있습니다. 원칙은 양방향
입니다.

> **미러는 prod가 거부할 것을 거부해야 하고, prod가 허용하는 상태를 만들 수 있어야 한다.**

앞의 절반은 쓰기를, 뒤의 절반은 읽기를 지킵니다. 미러가 prod보다 **느슨하면** prod가 거부할 행을
테스트가 통과시키고, **엄격하면** prod에 실재하는 상태를 픽스처가 재현하지 못해 그 상태를 지나는
읽기 경로가 검증되지 않은 채 남습니다. 둘 다 거짓 통과입니다.

항목별 기준은 이 원칙에서 유도합니다.

| 항목 | 기준 | 이유 |
| --- | --- | --- |
| 제약 (`NOT NULL`·`CHECK`·`UNIQUE`) | 이 서버가 **쓰는** 테이블이면 레거시와 **똑같이**. 더 느슨해도 더 엄격해도 안 됩니다 | 양방향 원칙이 직접 적용되는 유일한 항목입니다 |
| 외래 키 | 쓰는 테이블이면 유지. 읽기 전용 테이블은 **새 모듈 의존이 생기면 두지 않습니다** | FK도 제약이지만, 읽기 전용이면 우리가 위반할 쓰기가 없어 픽스처 현실성만 좌우합니다 |
| 컬럼 범위 | 엔티티가 매핑하는 컬럼. 기본값 없는 `NOT NULL` 컬럼은 매핑하지 않아도 만듭니다 | 매핑하지 않는 컬럼은 읽지도 쓰지도 않아 prod 실패를 만들 수 없습니다. 단 픽스처 INSERT는 막힙니다 |
| 인덱스 | 필요할 때만. 기본은 만들지 않습니다 | 정확성에 영향이 없습니다. 계획을 테스트로 고정할 때만 필요합니다 |
| PK `DEFAULT uuid_generate_v4()` | **무관**. 있어도 없어도 됩니다 | 없어서 생기는 것은 거짓 통과가 아니라 거짓 실패(픽스처가 id를 빠뜨리면 local에서만 실패)이고, 안전한 방향입니다 |

몇 가지를 덧붙입니다.

**외래 키.** prod의 `ddl-auto=validate`는 FK를 검증하지 않으므로 재현 자체로 얻는 것은 없습니다.
쓰는 테이블에서 FK가 필요한 이유는 우리 쓰기가 고아 행을 만들 수 있고 prod가 그것을 거부하기
때문입니다. 읽기 전용 테이블에는 그 경로가 없으므로, FK 하나 때문에 모듈 의존이 새로 생긴다면
두지 않습니다. `:memory`가 `workspaces`·`users` FK를 빼고 모듈 안에서 닫히는
`confirmed_memories.created_from_candidate_id`만 남긴 것이 이 경우입니다.

**인덱스.** `local`/`test`의 데이터량에서는 seq scan이 더 싸서 인덱스가 있어도 계획에 잡히지
않습니다. 값어치는 성능이 아니라 **계획을 테스트로 고정할 수 있다**는 데 있습니다. `SET LOCAL
enable_seqscan = off` 후 실제 SQL을 `EXPLAIN`해 인덱스가 후보로 오르는지 보면, 술어와 부분 인덱스
조건이 어긋나는 것을 잡을 수 있습니다(`MinsuLedgerMetricsIntegrationTest`). 그런 테스트를 둘
때 그 인덱스를 만듭니다. 미리 만들어 두는 것은 죽은 DDL입니다.

**이미 존재하는 환경.** `dev`처럼 대상 테이블이 이미 있는 환경이 있으면 `CREATE TABLE IF NOT
EXISTS`를 씁니다. 그렇지 않으면 실배포에서 Flyway가 `relation already exists`로 죽습니다
(`entity_relations`, MOM-0795). `local`/`test`는 매번 빈 DB라 이 충돌이 재현되지 않으므로 대상
환경을 착수 시점에 확인합니다.

기존 미러를 이 원칙으로 대조한 결과는 다음과 같습니다.

- **쓰는 테이블의 제약 누락은 없습니다.** `users`·`workspaces`·`workspace_members`·
  `workspace_label_sequences`·`projects`·`tasks` 여섯 모두 레거시의 `NOT NULL`·`CHECK`·`UNIQUE`·FK를
  그대로 갖습니다.
- **한 곳이 prod보다 엄격합니다.** `tasks.workspace_id`가 미러에서는 `NOT NULL`이지만 레거시
  (`000006_fe_contract.sql`)에서는 nullable입니다. 레거시가 기존 행을 백필하고 트리거로 채우므로
  실제 `NULL` 행은 사실상 없고, `validate`도 nullability를 보지 않아 기동을 막지 않습니다. 기록만
  남기고 고치지 않습니다.
- 읽기 미러(`source_refs`·`entity_relations`)의 PK 기본값 없음과 인덱스 없음은 **어긋남이
  아닙니다.** 위 기준에서 각각 "무관"과 "필요할 때만"에 해당합니다.

## 시간 · 식별자

레거시 `momens-api` 스키마와 호환을 유지합니다.

### 식별자

- PK는 UUID v4 (Java `UUID` ↔ Postgres `uuid`).
- UUID는 앱 측에서 생성합니다(엔티티 생성 시 `UUID.randomUUID()`). 스키마의
  `DEFAULT uuid_generate_v4()`는 안전망으로 둘 수 있습니다.
- 조인 테이블은 복합 PK를 사용할 수 있습니다.
- 예외: append-only 발행 로그(`outbox_events`)의 PK는 소비자의 polling watermark라
  단조 증가하는 `bigserial`을 씁니다(ADR-0008). watermark는 순서가 보장되는 정수여야 하고
  UUID로는 그 역할을 할 수 없습니다.

### 시간

- 시간 타입은 `timestamptz` ↔ Java `Instant`. 저장·처리 모두 UTC 기준이며, 표시(타임존
  변환)는 클라이언트가 담당합니다.
- 날짜만 필요한 값은 `DATE` ↔ `LocalDate`.

### 감사 필드

- 모든 테이블에 `created_at`/`updated_at`(`NOT NULL`)을 둡니다.
- 값은 JPA Auditing(`@CreatedDate`/`@LastModifiedDate`)으로 채웁니다. 스키마의
  `NOT NULL DEFAULT NOW()`는 안전망으로 둡니다.
- 예외: append-only 발행 로그(`outbox_events`)는 행이 수정되지 않으므로 `updated_at`을 두지 않고
  `created_at`만 둡니다.

### 소프트 삭제

- `deleted_at`(`timestamptz`, nullable)로 소프트 삭제를 표현합니다. 적용 범위는
  레거시 기준으로 엔티티별로 판단합니다.
