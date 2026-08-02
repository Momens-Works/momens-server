# PR #123 상세 리뷰 보고서

- PR: `[Feature] Minsu 생성 원장 스키마와 영속성 도입`
- 브랜치: `MOM-0817-feat/minsu-generation-ledger-schema`
- 기준 브랜치: `origin/develop`
- Momens 작업: `MOM-0817 · [Feat] Minsu 생성 원장 스키마와 영속성 도입`
- 작업 상태: `in_progress`
- 리뷰 일자: 2026-08-03
- 결론: **수정 요청(Request changes)**

## 발견사항

### 1. High — claim CHECK가 token/lease 일부 잔존을 허용함

대상:

- `modules/minsu/src/main/resources/db/migration/V20260803090000__create_minsu_task_draft_generations.sql:64`
- `modules/minsu/src/test/java/works/momens/server/minsu/internal/ledger/TaskDraftGenerationRepositoryIntegrationTest.java:95`

현재 조건은 다음과 같다.

```sql
CHECK (
    (status = 'processing')
    =
    (claim_token IS NOT NULL AND lease_expires_at IS NOT NULL)
)
```

`status = 'pending'`, `claim_token IS NOT NULL`, `lease_expires_at IS NULL`인 경우 양쪽이 모두
`false`가 되어 CHECK를 통과한다. 반대로 token은 없고 lease만 남아 있는 상태도 통과한다.

따라서 현재 제약은 주석과 설계에서 말하는 다음 불변식을 강제하지 못한다.

- `processing`이면 token과 lease가 모두 존재한다.
- `pending` 또는 `completed`이면 token과 lease가 모두 비어 있다.
- retryable 실패로 `pending`에 되돌릴 때 token과 lease가 함께 정리된다.

권장 수정:

```sql
CHECK (
    ((status = 'processing') = (claim_token IS NOT NULL))
    AND
    ((status = 'processing') = (lease_expires_at IS NOT NULL))
)
```

테스트에는 다음 두 경우를 별도로 추가한다.

- `pending` 행에 `claim_token`만 설정하면 거부됨
- `pending` 행에 `lease_expires_at`만 설정하면 거부됨

현재 테스트는 두 필드를 동시에 설정하는 경우만 검사하기 때문에 이 구멍을 발견하지 못한다.

### 2. High — `apply_cutoff_at < read_deadline_at` 관계가 보장되지 않음

대상:

- `modules/minsu/src/main/resources/db/migration/V20260803090000__create_minsu_task_draft_generations.sql:36`
- `modules/minsu/src/test/java/works/momens/server/minsu/internal/ledger/TaskDraftGenerationRepositoryIntegrationTest.java:125`
- `docs/design/minsu-async-task-draft-design.md:736`

설계 8.6절은 `ready` 투영 이후 title이 변경되는 경합을 줄이기 위해 다음 순서를 요구한다.

```text
apply_cutoff_at < read_deadline_at
```

하지만 DDL에는 이 순서를 보장하는 CHECK가 없다. 통합 테스트 fixture는 두 값을 같은 시각으로
저장하고 있어 가드 밴드가 없는 행을 정상 상태로 취급한다.

이 상태에서는 반영 트랜잭션의 cutoff 판정과 읽기 경로의 deadline 투영이 같은 경계에서
경합하여, 앱이 `ready`와 fallback title을 받은 뒤 AI title이 반영될 가능성이 커진다.

권장 수정:

```sql
CONSTRAINT minsu_task_draft_generations_deadline_check
    CHECK (apply_cutoff_at < read_deadline_at)
```

통합 테스트도 다음을 검증해야 한다.

- 정상 가드 밴드가 있는 행은 저장됨
- 두 값이 같으면 거부됨
- `apply_cutoff_at`이 더 늦으면 거부됨

### 3. Medium — `invalid_config` 동작이 확정되지 않은 상태에서 제3의 의미를 기록함

대상:

- `modules/minsu/src/main/java/works/momens/server/minsu/internal/config/MinsuAsyncProperties.java:19`
- `modules/minsu/src/main/java/works/momens/server/minsu/internal/ledger/CompletionReason.java:24`
- `docs/design/minsu-async-task-draft-design.md:900`
- `docs/design/minsu-async-task-draft-design.md:1034`

현재 자료에는 서로 다른 세 동작이 존재한다.

1. 설계 9.2절: 원장을 적재하고 `invalid_config`로 terminal 처리
2. 설계 11.2절: 원장을 `pending`으로 유지하고 claim하지 않음
3. 이번 PR의 `MinsuAsyncProperties`: 설정이 무효면 원장을 적재하지 않음

각 선택은 관측성과 설정 복구 후 재처리 동작이 다르다.

- terminal 처리: 설정 오류가 원장과 completion 지표에 남지만 자동 복구되지 않음
- pending 유지: 설정 복구 후 이어서 처리할 수 있지만 deadline 전까지 `generating`
- 미적재: 동기 fallback으로 끝나며 설정 오류 작업이 원장에 남지 않음

이번 PR은 이 충돌을 명시적으로 확정하지 않고 세 번째 동작을 향후 MOM-0818/0819의 규칙으로
기록했다. 먼저 설계 문서에서 하나의 동작을 확정하고 다음을 함께 정렬해야 한다.

- `MinsuAsyncProperties` Javadoc
- `CompletionReason.INVALID_CONFIG`의 존치 여부와 의미
- 적재 조건
- scheduler claim 조건
- 관련 테스트 및 관측 지표

provider가 단순히 비활성인 경우 적재하지 않는다는 규칙은 기존 설계와 일치한다. 충돌 대상은
provider가 활성 상태지만 설정이 무효인 경우다.

### 4. Medium — snapshot의 필수 Signal 필드가 nullable임

대상:

- `modules/minsu/src/main/resources/db/migration/V20260803090000__create_minsu_task_draft_generations.sql:23`
- `modules/minsu/src/main/java/works/momens/server/minsu/internal/ledger/TaskDraftGeneration.java:40`
- `modules/signal/src/main/resources/db/migration/V20260707100000__create_signals.sql:14`

원장 DDL과 엔티티는 다음 snapshot 필드를 nullable로 둔다.

- `signal_title`
- `signal_type`
- `signal_description`

원본 `signals` 테이블에서는 세 필드가 모두 `NOT NULL`이며, 원장의 목적은 convert 시점 입력을
그대로 보존하는 것이다. 별도의 optional 결정이 없다면 원장에서도 `NOT NULL`로 유지하는 편이
fail-closed 정책과 일관된다.

권장 수정:

- DDL에 `signal_title`, `signal_type`, `signal_description`의 `NOT NULL` 추가
- 엔티티 `@Column`에 `nullable = false` 추가
- `signal_impact`는 원본 계약대로 nullable 유지
- 통합 테스트에서 필수 snapshot 값의 저장과 누락 거부 검증

## 설계 결정 검토

### `UNIQUE(task_id)`

현재 task draft 한 종류만 다루는 범위에서는 단순하고 합리적이다. 다른 생성 종류가 생기면
별도 테이블을 소유하게 한다는 근거도 현재 원장의 task-draft 전용 컬럼 구성과 잘 맞는다.

다만 MOM-0817이 검토 항목으로 명시한 **동일 종류 재생성**은 PR 근거에서 다루지 않는다.
`(task_id, generation_kind)` 역시 동일한 종류의 반복 생성을 직접 허용하지는 않는다. 향후
재생성 API가 생겼을 때 다음 중 어느 모델을 따를지만 PR 본문에 기록하는 것이 좋다.

- 기존 원장 행의 snapshot/baseline을 교체하고 다시 `pending`으로 여는 모델
- 세대 식별자 또는 별도 이력 테이블로 매 생성을 보존하는 모델
- 재생성 API를 지원하지 않는다는 현재 결정을 유지하는 모델

지금 구현할 필요는 없지만, 이번 결정이 어떤 미래 비용을 선택했는지는 남겨야 한다.

### description/impact 상한

원장 자체 상한을 두지 않는 결정은 snapshot 보존 목적과 일치한다. 적재 시 잘라내면 convert 시점
입력과 달라지고, 제약 위반으로 convert 전체가 실패하는 것도 fail-closed 경로에 추가 위험을
만든다.

다만 snapshot 보존·삭제 정책과 worker 길이 계약은 prod 마이그레이션 전 MOM-0825에서 반드시
확정되어야 한다.

### 설정 키

다음 세 축의 이름과 기본 비활성 값은 요구사항을 충족한다.

```yaml
momens.minsu.task-draft.enabled
momens.minsu.task-draft.async.enroll
momens.minsu.task-draft.async.drain
```

provider/enroll/drain을 분리해 신규 적재와 기존 원장 drain을 독립적으로 제어하는 방향도 설계와
일치한다. 단, 앞서 언급한 `invalid_config` 조합의 동작은 별도 확정이 필요하다.

## 범위 및 컨벤션 검토

- PR 변경은 MOM-0817의 원장 스키마·엔티티·repository·설정명 확정 범위와 대체로 일치한다.
- 적재·claim·반영 로직은 포함하지 않아 후속 MOM-0818/0819/0820 경계를 지킨다.
- 운영 마이그레이션을 MOM-0825로 분리한 것은 prod Flyway 비활성·레거시 단일 스키마 소유 규칙과
  일치한다.
- `minsu -> common` 및 JPA 의존 추가 후 Spring Modulith 검증이 통과한다.
- 브랜치, 커밋 메시지, PR 제목이 Git 규칙을 따른다.
- API 응답 계약 변경은 없다.
- PR diff에 시크릿이나 로컬 전용 설정 파일이 포함되지 않았다.

## 검증 결과

### 저장소 상태

- 작업트리: clean
- 기준: `origin/develop`
- divergence: base-only 0, head-only 1
- 변경: 13개 파일, 482 insertions, 2 deletions
- `git diff --check`: 통과

### 로컬 검증

- 신규 `TaskDraftGenerationRepositoryIntegrationTest` 강제 재실행: 통과
- `./gradlew spotlessCheck`: 통과
- `./gradlew test`: 통과
- `./gradlew bootJar`: 통과

### GitHub 검사

- `build`: 통과
- `pr-format`: 통과
- CodeQL/Analyze: 통과
- CodeRabbit: 체크는 pass지만 사용량 제한으로 실제 리뷰가 실행되지 않음
- PR merge 상태: 필수 승인 부재로 blocked, 코드상 merge conflict는 없음

## Momens 동기화

- 작업: `MOM-0817 · [Feat] Minsu 생성 원장 스키마와 영속성 도입`
- 상태: `in_progress`
- 우선순위: `medium`
- 프로젝트: `PRJ-0003 스프린트`
- 마일스톤: `모바일 고도화 (서버)`
- 담당자: 김규일

완료 조건 중 원장 테이블·엔티티, `BaseEntity` 사용, baseline 값 저장, 설정 키와 기본값,
`ApplicationModules.verify()`는 충족한다. 위 발견사항 중 claim 무결성, deadline 순서 및 설정 무효
동작을 정리한 뒤 최종 승인하는 것이 안전하다.
