# prod 스키마 주도권 이전 설계

작성일: 2026-08-23 · 상태: Current design · 근거 ADR: [ADR-0019](../adr/0019-prod-schema-ownership-transfer.md)

prod의 스키마 DDL 주도권을 레거시 `momens-api`에서 이 서버로 옮기는 설계다. 다섯 가지 결정을
확정하고, 실행 티켓(MOM-0909·MOM-0910)이 그대로 입력으로 쓸 수 있는 형태로 사실을 남긴다.

## 요약

| # | 결정 | 내용 |
| --- | --- | --- |
| 1 | 소유 범위 | 서버가 prod 스키마를 소유한다. 레거시 DDL은 동결한다 |
| 2 | baseline 방식 | `flyway_schema_history`에 기존 적용분을 심고 나머지만 실행한다. `outOfOrder`는 부트스트랩 1회만 켠다 |
| 3 | 레거시 정책 | 동결 대상은 **이 리포에 마이그레이션 파일이 있는 객체**로 한정한다 |
| 4 | 헤더·게이트 | `prod-schema` 헤더 4종과 스키마 릴리스 게이트를 폐지한다. 폐지 머지는 부트스트랩 prod 적용 이후 |
| 5 | 롤백 | fix-forward를 기본으로 한다. 완전 원복은 이력 심기 단계까지만 보장한다 |

## 1. 배경

prod는 레거시와 공유 DB를 쓰는 전환기라 이 서버의 Flyway가 꺼져 있고 `ddl-auto: validate`로 매핑만
검증한다. 서버가 만든 스키마가 prod에 없으면 **애플리케이션이 기동하지 않는다.** 현재 미반영 16건이
남아 있어 서버를 prod에 배포할 수 없고, 릴리스 게이트가 `develop` → `main` 병합도 막고 있다.

원래 계획(MOM-0840)은 이 16건을 `momens-api` 마이그레이션으로 옮겨 심는 것이었다. 두 가지로 멈췄다.
`signals` 계열의 반영 저장소가 확정되지 않았고(근거 티켓 MOM-74가 Momens 이관 때 유실), 시도했던
`momens-api#28`은 머지되지 않고 닫혔다.

방향을 바꿔 서버가 주도권을 가져간다. [이관 전략](legacy-product-api-migration/strategy.md)은
"DDL 소유권 이전 시점은 모든 Product API 이관이 끝난 뒤 별도로 결정한다"고 적고 있었는데, 이 설계가
그 시점을 앞당긴다. 근거는 ADR-0019에 있다.

## 2. 조사로 확인한 사실

모두 코드와 실제 DB 형상에서 확인했다. 관찰 대상은 `scripts/legacy-diff`의 컨테이너 두 개다 —
`legacy-db`는 레거시 러너가 `000001`~`000019`를 적용해 만든 **prod와 같은 형상**이고, `server-db`는
이 서버의 Flyway가 만든 형상이다.

### 2.1 공유 DB의 DDL writer는 둘이다

`persistence.md`와 대장은 "레거시 `momens-api`가 단일 소유"라고 적고 있으나 사실이 아니다.

| 주체 | 러너 | 이력 | 소유 | 마지막 DDL |
| --- | --- | --- | --- | --- |
| `momens-api` | 자체 Go 러너 (`internal/platform/db/migrations.go`) | `schema_migrations` | 제품 스키마 `000001`~`000019` | 2026-07-12 |
| `momens-worker` | 같은 러너 | `schema_migrations` | `raw_source_events` 등 worker 전용 | 2026-06-27 |
| `momens-retrieval` | 없음 (Flyway 미사용) | — | 없음. 읽기만 한다 | — |
| `momens-server` | Flyway 12.4.0 (prod off) | `flyway_schema_history` | 부트스트랩 후 | — |

`k8s/manifests/apps/momens-worker/configmap.yaml`이 `MIGRATIONS_ENABLED: "true"`를 명시적으로 켠다.
`ENV=production`의 기본값이 `false`인데 worker가 자기 전용 스키마를 소유하므로 뒤집은 것이다.
(`k8s/docs/secrets.md`는 아직 "the API stays the single migration owner"라고 적고 있어 매니페스트와
어긋난다. `k8s` 리포 문서라 여기서 고치지 않고 기록만 남긴다.)

### 2.2 두 이력 테이블은 충돌하지 않는다

레거시 러너는 `schema_migrations`(TEXT version PK, 체크섬 없음), Flyway는 `flyway_schema_history`를
쓴다. 서로를 읽지 않으므로 한 DB에서 독립적으로 공존한다. `schema_migrations`의 version은 파일명
전체(`000001_init`, `000001_worker_schema`)라 api와 worker도 같은 표에서 충돌하지 않는다.

### 2.3 마이그레이션 40건의 분류

| 헤더 | 건수 | prod 실물 |
| --- | --- | --- |
| `mirror` | 23 | 있다 (레거시가 만듦) |
| `applied momens-api#10` | 1 | 있다 (`refresh_tokens`, 레거시 `000018`) |
| `required` | 16 | 없다 |

**부트스트랩에서 심어야 할 집합은 미러 23건이 아니라 24건이다.** `V20260626023000__create_refresh_token.sql`은
미러가 아니지만 prod에 실물이 있다. 빠뜨리면 Flyway가 이 파일을 실행해 `relation already exists`로 죽는다.

### 2.4 version 배치가 섞여 있다

```
V20260624090000 ~ V20260707090000   심기 (미러 9 + applied 1)
V20260707100000 ~ V20260811090000   실행 (required 16)  ※ 중간에 미러 1건(V20260715100000)
V20260819090000 ~ V20260822000600   심기 (미러 14)
```

미러 14건이 실행 대상보다 **뒤에** 있다. 24건을 심으면 이력의 최고 version이 `20260822000600`이 되고,
실행해야 할 required는 전부 그보다 낮아진다. Flyway는 기본값 `outOfOrder=false`에서 이미 적용된 최고
version보다 낮은 미적용 마이그레이션을 실행하지 않는다.

이 동작은 추론이 아니라 이 리포에서 실제로 일어나 있다. `legacy-diff`의 `server-db` 이력을 보면
`20260821100200`과 `20260821140000`은 있는데 그 사이의 `20260821110000`(workspace invitations mirror)만
없다. 그 DB가 `...140000`까지 진행한 뒤에 `...110000` 파일이 리포에 들어왔고, Flyway가 **에러 없이
`success=t`로 건너뛰었다.** 테이블만 존재하지 않는다.

미러는 계속 위쪽에서 늘고 있다(8/19 3건, 8/21 7건, 8/22 1건). 부트스트랩을 미루면 격차만 벌어진다.

### 2.5 서버가 파일을 갖지 않는 레거시 테이블이 11개 있다

```
decisions, sync_states,
oauth_access_tokens, oauth_authorization_codes, oauth_clients,
oauth_grants, oauth_interactions, oauth_refresh_tokens,
retrieval_cursors, retrieval_documents, retrieval_events
```

`oauth_*` 6개는 MCP OAuth(레거시 `000019`, 레거시의 가장 최근 DDL)이고 아직 이관 전이다(MOM-0871).
`retrieval_*`는 worker/retrieval 투영 테이블이다. 이 서버에는 엔티티도 마이그레이션도 없다.

### 2.6 미러가 재현하지 않는 prod 컬럼이 5개 있다

미러는 "엔티티가 매핑하는 컬럼 + 기본값 없는 `NOT NULL` 컬럼"만 재현한다는 규칙 때문에, prod에는 있으나
서버 파일이 만들지 않는 컬럼이 있다.

| 테이블 | 컬럼 |
| --- | --- |
| `entity_relations` | `metadata`, `source_ref_ids`, `weight` |
| `source_refs` | `content_hash` |
| `workspace_members` | `onboarding_state` |

의도된 것이고 `validate`가 매핑된 컬럼만 보므로 기동을 막지 않는다. 다만 주도권 이전 후에도
**local과 prod가 문자 그대로 같아지지는 않는다**는 뜻이므로 기록해 둔다. `tasks`는 미러가 레거시 컬럼을
전부 재현하고 있어 해당이 없다.

### 2.7 `tasks`를 건드리는 required 5건의 객체 단위 판정

| 파일 | prod 실행 | 이유 |
| --- | --- | --- |
| `V20260707120000` add_task_detail_and_checklist | **부분 충돌** | `description`·`assignee_id`가 prod에 이미 있다. `task_checklist_items`만 필요하다 |
| `V20260707150000` task_role_single_value | **실행 불가** | 아래 참조 |
| `V20260714091000` add_task_origin | 안전 | `origin_type`·`origin_signal_id`가 prod에 없다 |
| `V20260715090000` task_role_drop_not_null | **실행 불가** | 위 파일이 만드는 컬럼에 의존한다 |
| `V20260716090000` add_task_minsu_fields | 안전 | `next_action`·`task_open_questions`가 prod에 없다 |

나머지 11건은 prod에 아예 없는 순수 신규 테이블이라 충돌 여지가 없다.

### 2.8 `task_roles`는 prod에 존재한 적이 없다

`V20260707150000__task_role_single_value.sql`은 `task_roles`를 읽어 `tasks.role`을 백필하고
`DROP TABLE task_roles`로 끝난다. 그런데 `task_roles`를 만드는 것은 이 리포의
`V20260706120000__create_task.sql`이고, **그 파일의 헤더는 `mirror`다.**

- `momens-api/migrations/` 전체에 `task_roles` 문자열이 없다.
- 레거시 형상 DB의 `task_roles` 개수: `0`

`tasks`는 레거시 소유가 맞지만 `task_roles`는 이 서버가 자체적으로 만든 테이블이고 prod에 간 적이 없다.
한 파일이 두 성격을 갖는 사례이며, 이번에는 `required`가 아니라 **`mirror` 헤더가 잘못 붙어 있던 경우**다.
`persistence.md`가 경고한 "잘못 `mirror`로 두면 게이트가 조용히 통과시킨다"가 실제로 일어나 있었다.

prod에서 이 파일은 두 번 실패한다. `UPDATE`에서 `relation "task_roles" does not exist`로 한 번,
그것을 넘겨도 기존 `tasks` 행의 `role`이 전부 `NULL`이라 `SET NOT NULL`에서 다시 한 번.
**어떤 조건에서도 실행할 수 없다.**

## 3. 결정 1 — 소유 범위

**서버가 prod 스키마를 소유하고, 레거시 DDL을 동결한다.**

`tasks`를 ALTER 하는 required가 5건이므로 "서버 신규 객체만 소유"는 성립하지 않는다. 실질 선택지는
전체 소유이거나 객체별 분할 규칙이었고, 전체 소유로 정했다.

객체별 분할(레거시 테이블은 레거시가, 서버가 필요한 컬럼은 서버가)을 택하지 않은 이유는 두 가지다.
첫째, 한 객체에 writer가 둘이 되고 두 이력이 서로를 모르므로 순서 보장이 없다. `tasks`가 이미 그
상태가 된다. 둘째, **미러가 살아남으면서 prod에서 실행되기 시작한다.** 지금 미러가 안전한 유일한
이유는 prod에서 Flyway가 꺼져 있어서다. 부트스트랩 이후에 새로 쓰는 미러는 prod에서 실제로 돌아가므로
전부 `IF NOT EXISTS` 방어를 달아야 한다.

레거시가 지키려는 DDL 자율성은 **2026-07-12 이후 행사되지 않았다.** 그 대가로 위 두 비용을 영구화할
이유가 없다.

## 4. 결정 2 — baseline 방식

**`flyway_schema_history`에 기존 적용분을 심고 나머지만 실행한다. `outOfOrder`는 부트스트랩 릴리스에서만 켠다.**

### 심는 방법

빈 스크래치 DB에 Flyway를 그대로 돌려 전체를 적용시킨 뒤, 생성된 `flyway_schema_history`에서 해당
행들을 그대로 뽑아 prod에 `INSERT` 한다. **체크섬을 직접 계산하지 않는다.** Flyway는 매 기동 시 파일을
다시 해싱해 이 값과 대조하고 다르면 `checksum mismatch`로 기동을 거부하므로, CRC32를 재구현하면
어긋났을 때 원인 추적이 어렵다. INSERT는 단일 트랜잭션으로 감싼다.

### 심는 집합은 파일 목록이 아니라 객체 대조가 정한다

2.3의 24건이 출발점이지만 최종 집합이 아니다. 2.7·2.8에서 확인했듯 실행 대상 16건 중 최소 3건이
그대로 실행될 수 없다. **실행 집합은 "이 파일이 만드는 객체 중 prod에 없는 것"으로 판정하며,
파일 단위로 옮기지 않는다.**

방향은 실행 불가·부분 충돌 파일을 심기 쪽으로 옮기고, 그 **순효과만** 만드는 보정 마이그레이션을
오늘 날짜 version으로 새로 쓰는 것이다. 예를 들어 `task_role_single_value`와 `task_role_drop_not_null`의
prod 순효과는 `tasks.role TEXT`(nullable) + `tasks_role_check` 뿐이다. 이렇게 하면 실행 집합이 위쪽
version으로 올라가므로 `outOfOrder` 의존도 함께 줄어든다.

정확한 분할과 보정 마이그레이션 작성은 MOM-0909가 소유한다.

### `outOfOrder`

2.4 때문에 심기 방식은 `spring.flyway.out-of-order=true`를 동반한다. 없으면 실행 대상이 조용히
건너뛰어져 기동 후 `validate`에서 죽거나, `validateOnMigrate`가 먼저 실패한다.

부트스트랩이 끝나면 이력의 최고 version이 리포 최고 version과 같아지고 이후 새 마이그레이션은 항상 그보다
뒤에 온다. 따라서 **부트스트랩 릴리스에서만 켜고 다음 릴리스에서 되돌린다.** 상시로 두면 오래된 브랜치의
낮은 version 마이그레이션이 나중에 조용히 적용되는 통로가 영구히 남는다. 이 리포는 여러 브랜치가 병렬로
진행되므로(하루에 미러 7건이 들어온 적이 있다) 현실적인 위험이다.

### 채택하지 않은 대안 — 실행 대상 재번호 + `baselineVersion`

실행 대상 파일들을 현재 최고 version 뒤로 rename 하면 미러와 version 구간이 분리되어 `baselineVersion`
설정만으로 끝나고, INSERT 스크립트도 `outOfOrder`도 필요 없다.

택하지 않은 이유는 파일명이 Flyway에게 신원이기 때문이다. rename 하면 local은 DB 재생성으로 넘어가지만
**dev는 서버 Flyway가 실제로 소유·운영 중인 배포 환경**이라 이력에 옛 이름이 남는다. 결국 일회성
스크립트가 사라지는 게 아니라 prod에서 dev로 옮겨갈 뿐이고, 그 대가로 7월에 쓴 마이그레이션이 8월
번호를 갖게 되어 version이 작성 시점을 뜻하지 않게 된다. 심기 방식은 **파일을 한 글자도 건드리지
않으므로** local·dev·test에 영향이 없다.

## 5. 결정 3 — 레거시 마이그레이션 정책

**동결 대상은 "이 리포에 그 객체를 만드는 마이그레이션 파일이 있는가"로 판정한다.**

부트스트랩 시점의 파일 집합이 경계를 정의한다. 그 안쪽(서버가 매핑하는 공유 제품 스키마)은 레거시와
worker가 DDL을 하지 않는다. 바깥쪽(2.5의 11개 + worker 전용 스키마)은 각자가 계속 소유한다. 이후 새로
만드는 객체는 만든 쪽이 소유한다.

레거시를 전면 동결하지 않는 이유는 `oauth_*` 때문이다. MCP OAuth는 레거시의 가장 최근 DDL 영역이고 아직
이관 계획도 확정되지 않았다(MOM-0871). 서버가 매핑하지도 않는 테이블의 마이그레이션을 이 리포가 들고
있으면 죽은 DDL만 늘어난다.

위험의 원천은 "한 DB에 러너가 여럿"이 아니라 **"한 객체에 writer가 둘"**이다. api와 worker는 이미 한
DB에서 문제없이 공존해 왔다(2.1·2.2). 막아야 하는 것은 겹치는 객체뿐이다.

**강제 수단은 없다.** 레거시 러너는 계속 동작하므로 동결은 규율로 지켜진다. 위반은 다음 서버 배포의
`ddl-auto: validate`가 기동에서 사후에 잡는다. `validate`는 매핑된 컬럼의 존재와 타입만 보고 제약·기본값·
UNIQUE는 보지 않으므로 완전한 탐지가 아니다. 결정 1을 택한 이상 감수하는 비용이다.

동결은 레거시(신진수)와 worker 양쪽의 합의가 필요하다.

## 6. 결정 4 — 헤더 체계와 릴리스 게이트

**`prod-schema` 헤더 4종과 스키마 릴리스 게이트를 폐지한다.**

헤더 4종(`mirror`/`required`/`pending`/`applied`)은 전부 "다른 저장소가 이 스키마를 prod에 반영해 줘야
한다"는 크로스 리포 의무의 상태다. 부트스트랩 이후에는 그 의무가 존재하지 않는다. 서버 Flyway가 배포
때 직접 적용하므로 "반영 여부"가 "배포됐는가"와 같아진다. dev가 이미 그렇게 돌아가고 있다.

미러 충실도 규칙도 같다. 그 규칙이 필요했던 이유는 local/test와 prod가 서로 다른 파일에서 만들어진 두
스키마 세계였기 때문인데, 이후에는 네 환경이 같은 파일에서 나온다(2.6의 5개 컬럼은 예외로 남는다).

폐지 후의 안전망은 **Flyway 자체**다. 잘못된 마이그레이션은 배포 시점에 Flyway가 실패시킨다. 지금 dev가
보호받는 방식과 같다.

### 폐지가 파일 수정을 뜻해서는 안 된다

기존 40건의 헤더 주석은 **지우지 않는다.** 주석 한 줄이라도 고치면 Flyway checksum이 바뀌어 local·dev
이력이 깨진다. 헤더는 역사적 주석으로 그대로 두고, 부트스트랩 이후 새로 쓰는 파일에만 헤더를 달지 않는다.

### 순서가 강제된다

**게이트 폐지 머지는 부트스트랩이 prod에 실제로 적용된 뒤여야 한다.** 지금 게이트는 오작동하는 것이
아니라 정확히 제 일을 하고 있다. 미반영 스키마가 prod에 나가면 서버가 기동하지 않으므로 막는 것이 맞다.
먼저 풀면 그 사이 릴리스가 prod를 죽인다.

MOM-0910은 작성을 MOM-0909와 병행할 수 있으나 **머지 순서는 부트스트랩 적용 이후**다.

대장 문서(`docs/prod-schema-ledger.md`) 자체는 남는다. 선언 구간(prod 필수 환경변수)과 수기 구간은
MOM-0841 소유이며 이 결정의 영향을 받지 않는다. 제거 대상은 생성 구간과 `--release-check`의 스키마 검사다.

## 7. 결정 5 — 롤백

**fix-forward를 기본으로 한다.**

### 단계와 복구

| 단계 | 실패 시 |
| --- | --- |
| ① 스크래치 DB에서 체크섬 산출 | prod 무관 |
| ② 리허설 | prod 무관 |
| ③ prod에 이력 INSERT | **완전 원복 가능.** `DROP TABLE flyway_schema_history`. 이 시점 서버는 아직 `flyway.enabled=false`라 그 표를 읽지 않는다 |
| ④ 서버 배포 (flyway on + outOfOrder) — 기동 전 실패 | 스키마 무변경. 배포 롤백 후 ①부터 재시도 |
| ④ 서버 배포 — 실행 중 실패 | **되돌리지 않는다.** 부분 적용 상태를 기록하고 원인 수정 후 재배포로 이어서 진행한다 |
| ⑤ 다음 릴리스에서 `outOfOrder` 제거 | 긴급도 낮음. 다음 정기 릴리스에서 처리 |

fix-forward가 기본인 이유는 **부트스트랩 중 사용자 영향이 0**이기 때문이다. 레거시가 계속 서비스하고
있고 서버는 기동에 실패해도 이전 Pod가 유지된다. 서두른 원복보다 정확한 수정이 낫다. 부분 적용된
객체들은 prod에 없던 신규 테이블·컬럼이라 레거시가 읽지도 쓰지도 않으므로 남아 있어도 무해하다.

역스크립트를 준비하는 방식(roll-backward)은 택하지 않았다. 스크립트 자체가 `DROP`문 덩어리라 사고 시
손실이 더 크고, 되돌려서 얻는 것이 "레거시가 안 쓰는 빈 테이블이 사라지는 것"뿐이다.

### 롤백 절차보다 우선하는 원칙

**부트스트랩 실행 집합에는 `DROP`과 파괴적 `UPDATE`가 하나도 없어야 한다.** 2.8의 `DROP TABLE task_roles`
같은 문장이 prod에서 한 번 돌면 어떤 사후 절차로도 되돌릴 수 없다.

이 확인은 리허설이 아니라 **객체 대조에서 끝나야 한다.** 리허설 DB에는 prod 데이터가 없어서
`SET NOT NULL` 실패 같은 것을 재현하지 못한다.

## 8. 실행 순서

```
MOM-0908 (이 문서 + ADR-0019)
   └─ MOM-0909  부트스트랩 스크립트 + prod 설정 + 리허설
        └─ [prod 적용]
             └─ MOM-0910  헤더·대장·게이트 폐지 (머지는 여기서)
```

배포 순서는 다음과 같다.

1. prod에 이력 INSERT (서버는 아직 flyway off)
2. 서버 배포 — `flyway.enabled=true`, `out-of-order=true`
3. 기동과 스키마 확인
4. 다음 릴리스에서 `out-of-order` 제거
5. MOM-0910 머지 — 헤더·게이트 폐지

### 리허설 환경은 이미 있다

MOM-0909가 요구하는 "prod와 같은 형상(레거시 러너가 만든 스키마 + Flyway 이력 없음)"은
`scripts/legacy-diff`의 `legacy-db`가 그대로 만족한다. 별도 구축이 필요 없다.

단, 리허설 DB에는 prod 데이터가 없다. 기존 행이 있어야만 드러나는 실패(`SET NOT NULL`, CHECK 위반)는
리허설로 잡히지 않으므로 객체 대조로 미리 닫는다.

## 9. 남은 위험

| 위험 | 대응 |
| --- | --- |
| 레거시·worker가 동결을 어긴다 | 강제 수단 없음. 합의 + 다음 배포의 `validate`로 사후 탐지. 제약·기본값·UNIQUE 변경은 탐지되지 않는다 |
| 체크섬 불일치로 기동 실패 | 스크래치 DB에서 Flyway가 계산한 값을 그대로 복사한다. 실패해도 스키마 무변경 |
| `mirror` 헤더가 더 잘못 붙어 있다 | 2.8이 한 건 드러났다. MOM-0909의 객체 대조가 실행 집합 전체를 다시 판정한다 |
| MOM-0910이 부트스트랩 전에 머지된다 | 6절의 순서 제약. 릴리스 게이트가 그때까지는 유지된다 |
| local·prod가 완전히 같지 않다 | 2.6의 5개 컬럼. 의도된 것이며 `validate` 대상이 아니다 |

## 관련 문서

- [ADR-0019 prod 스키마 주도권을 서버로 이전](../adr/0019-prod-schema-ownership-transfer.md)
- [데이터 규칙](../rules/persistence.md)
- [prod 운영 준비 대장](../prod-schema-ledger.md)
- [레거시 Product API 이관 전략](legacy-product-api-migration/strategy.md)
