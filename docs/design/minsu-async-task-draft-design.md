# MOM-0810 Minsu 풍부한 task draft 비동기 생성 설계

작성일: 2026-08-01

상태: 확정 설계 (구현 전)

관련 결정: [ADR-0011](../adr/0011-signal-evidence-and-task-draft-contract.md),
[ADR-0015](../adr/0015-minsu-async-task-draft-generation.md),
[ADR-0014](../adr/0014-minsu-task-draft-module-and-llm-boundary.md)(0015가 supersede),
[ADR-0008](../adr/0008-outbox-worker-projection-boundary.md)

관련 문서: [MOM-0803 동기 task draft 상세 설계](minsu-signal-task-draft-design.md),
[Signal push demo 설계](signal-push-demo-design.md)

## 1. 목적

`POST /api/mobile/signals/{signalId}/actions/convert-to-task`는 현재 Minsu task draft
생성을 동기로 기다린다. 모델 생성의 긴 tail latency가 사용자 요청 시간을 그대로 늘리므로,
즉시 사용할 수 있는 기본 draft를 먼저 반환하고 풍부한 생성 결과를 비동기로 결합하는 구조를
설계한다.

이 문서는 설계만 확정한다. 구현은 후속 티켓으로 분리한다.

## 2. 범위

### 포함

- draft 생성 상태와 전이 정의
- 비동기 생성 trigger 시점과 결과 저장 소유권
- 프로세스 재시작에도 유실되지 않는 after-commit 전달 방식
- 중복 실행 멱등성, 실패 복구, 사용자 수정과 AI 결과의 경합 방지
- 모바일 결과 확인 방식과 API 계약 영향
- 비동기 작업의 timeout·retry·관측 정책
- 동기 계약에서의 단계적 전환 방안
- 지연 예산 확대 후 Agentic RAG 성립 여부 검토
- 앱·ingress·서버 3구간 timeout 예산 정리

### 제외

- 이 티켓에서의 비동기 생성 구현
- 별도 마이크로서비스 분리
- prod 활성화
- MOM-0806에서 확정한 동기 호출 8초 timeout 구현 변경

## 3. 현재 동기 경로와 문제

`SignalActionServiceImpl.convertToTask`의 현재 순서는 다음과 같다.

```text
1. SignalReader.findLive + workspace membership 확인
2. SignalActionRepository.findBySignalId
3. 기존 action이면 replay/충돌 반환 (generator 미호출)
4. evidence 조회 (의미 있는 앞선 10건)
5. SignalTaskDraftGenerator.generate       ← 동기 LLM 호출, 쓰기 트랜잭션 밖
6. SignalActionExecutor.convert            ← tasks + signal_actions + outbox 2건 원자 저장
```

5번이 사용자 요청 시간에 그대로 더해진다. MOM-0806 로컬 측정에서 warm provider 구간은
p50 1,258ms이지만 p95 5,960ms / max 6,161ms로 tail 변동이 컸다. 30건 중 19건이 1,500ms
미만인데 3건이 5,000ms 이상이었다.

구조적 문제는 세 가지다.

**첫째, 지연 예산이 응답 시간에 묶여 있다.** 8초 timeout은 원탭 action의 사용자 체감을
기준으로 정한 값이라, 검색·재검토를 반복하는 생성 구조(Agentic RAG 등)를 이 예산 안에
넣을 수 없다. 품질을 올리려면 예산을 늘려야 하고, 예산을 늘리면 동기 경로에서는 사용자가
그만큼 기다린다.

**둘째, 8초 timeout이 실제 상한이 아니다.** `HttpOptions.timeout`은 SDK가 OkHttp `Call`을
실행하는 구간에만 걸린다. 그 앞의 ADC 탐색·client 생성·`refreshIfExpired()` token 갱신과
최초 client 생성을 기다리는 동시 요청은 이 8초 밖이다. MOM-0806의 15.2절 검증에서
`MOMENS_MINSU_LLM_TIMEOUT=10ms`로도 전체 API 889ms / provider observation 705ms가 나온
것이 이 구간의 존재를 보여준다.

**셋째, 품질과 무관하게 fallback 비율이 지연에 연동된다.** timeout에 걸리면 고정 fallback
(Signal title / `pm` / `medium`)이 저장되고, 사용자는 그것이 모델 결과인지 fallback인지
알 수 없다. 지연이 큰 Signal(evidence가 많고 context가 긴 Signal)일수록 fallback 확률이
높은데, 그런 Signal이야말로 모델 생성의 가치가 큰 쪽이다.

## 4. 3구간 timeout 예산

현재 세 구간의 값은 다음과 같다.

| 구간 | 값 | 출처 | 성격 |
| --- | --- | --- | --- |
| 앱 | 10초 | OkHttp 기본값 | 명시적으로 정한 값이 아님 |
| ingress | 60초 | ingress-nginx 기본값 | 명시적으로 정한 값이 아님 |
| 서버 LLM 호출 | 8초 | MOM-0806 팀 합의 | 유일하게 근거를 두고 정한 값 |

앱 10초와 ingress 60초는 둘 다 프레임워크 기본값이며 Momens가 예산으로 설계한 값이 아니다.
서버 8초만 측정에 근거한 합의값이다.

이 조합에서 **앱이 서버보다 먼저 끊는 창**이 존재한다. 서버 8초는 OkHttp `Call` 구간에만
걸리고 그 앞단(ADC 탐색·client 생성·token 갱신)과 뒷단(fallback draft 생성, task/action/outbox
저장, 커밋, 직렬화)은 측정되지 않았다. cold 최초 호출에서 앞단이 2초를 넘으면 전체 요청이
앱의 10초를 넘길 수 있고, 그때 앱은 요청을 실패로 처리하지만 서버는 task를 정상 생성한다.

이 불일치의 실제 사용자 노출은 다음과 같다. 앱이 끊어도 서버 트랜잭션은 커밋되므로 task는
생성된다. 사용자가 재시도하면 `signal_actions UNIQUE(signal_id)`에 걸려 replay 경로로
들어가 200과 기존 task를 받는다. 즉 **중복 task는 생기지 않고**, 사용자는 최초 시도에서
실패를 본 뒤 재시도에서 성공을 본다. 데이터 정합성 문제는 아니지만 체감은 나쁘다.

미측정으로 남은 구간은 다음과 같으며 dev 재측정 항목에 포함한다.

- ADC 탐색과 SDK client 최초 생성
- 만료 credential의 token refresh가 발생하는 호출
- 최초 client 생성을 기다리는 동시 요청의 대기
- fallback draft 생성부터 커밋·직렬화까지의 뒷단
- ingress timeout과 서버 timeout의 선후 관계

비동기 전환은 이 문제의 상당 부분을 구조적으로 없앤다. LLM 호출이 요청 경로에서 빠지면
동기 구간은 DB 쓰기만 남아 수십 ms 수준이 되고(MOM-0806 측정에서 `전체 API - provider`는
p50 43ms / max 117ms), 앱·ingress timeout과 경쟁하지 않는다.

## 5. after-commit 전달 방식

비동기 생성은 convert 트랜잭션이 **커밋된 뒤에** 시작해야 한다. 커밋 전에 시작하면 롤백된
task에 대해 모델을 호출하게 되고, 결과를 쓸 대상이 없다. 동시에 프로세스가 재시작해도
작업이 유실되지 않아야 한다.

### 5.1 후보

**(a) `@Async`** — 가장 단순하지만 작업이 JVM 힙의 스레드풀 큐에만 존재한다. 배포·OOM·
Pod eviction으로 프로세스가 내려가면 큐에 있던 작업과 실행 중이던 작업이 그대로 사라진다.
어떤 task가 풍부화되지 않았는지 알 방법도 없어 복구 진입점이 없다. Kubernetes 롤링 배포가
일상인 환경에서 배포할 때마다 일부 task가 조용히 fallback draft로 남는다. **채택하지 않는다.**

**(b) `@TransactionalEventListener(AFTER_COMMIT)` + Modulith event publication registry** —
커밋 시점 보장은 정확하고, `event_publication` 테이블이 미완료 event를 남겨 재시작 복구
진입점이 생긴다. 다만 이 레포는 아직 application event를 도입하지 않았고
(`docs/rules/persistence.md`가 "첫 application event 도입 시 `event_publication`을 Flyway로
추가"로 남겨둔 상태), 재발행 스케줄러와 운영 규약을 새로 만들어야 한다.

**(c) outbox 소비** — convert 트랜잭션이 이미 쓰는 `signal.converted_to_task`를 `minsu`가
소비해 원장을 materialize한다. `notification`의 watermark·polling 패턴을 그대로 쓸 수 있다.

**(d) convert 트랜잭션에서 원장을 직접 적재** — convert가 `tasks`·`signal_actions`·outbox와
같은 트랜잭션에서 `minsu` 생성 원장을 `pending`으로 함께 쓴다. 커밋 후에는 scheduler가 원장을
직접 claim한다. 원장 자체가 durable queue다.

### 5.2 (c)를 채택하지 않은 이유

(c)는 `notification` 선례를 그대로 쓸 수 있어 처음 후보였지만, 실제 계약을 확인하니 세 가지가
막힌다.

**task_id를 얻을 수 없다.** `SignalActionExecutor.convert`는 `aggregateId`에 Signal ID를 넣고
task_id는 payload에만 담는데, 공개 `OutboxEventView`에는 payload 필드가 없다("지금의 유일한
소비자가 payload를 쓰지 않으므로 담지 않는다"). consumer가 어떤 task를 처리할지 식별할 수
없다.

**최초 watermark 시드가 작업을 버린다.** `SignalCreatedDeliveryMaterializer`는 offset이 없으면
`latestIdBefore(safetyLag)`로 시드한다. 과거 알림을 소급 발송하지 않으려는 의도적 정책인데,
생성 작업에 그대로 쓰면 시드 이전에 커밋된 convert가 영구히 누락된다. 알림은 놓쳐도 되지만
생성 작업은 아니다.

**커밋과 materialize 사이에 상태가 없다.** convert 응답 시점에는 outbox row만 있고 원장 행이
없다. 7.1절 상태표를 적용할 대상이 없어, 응답의 `draft_status`를 feature flag만 보고 정해야
한다. 그러면 rollout·rollback 뒤 과거 task의 상태 해석이 달라진다.

이 셋은 각각 outbox 공개 계약 확장, 별도 cutover 규칙, durable intent 마커로 개별 대응할 수
있지만, (d)는 셋을 한꺼번에 없앤다.

### 5.3 채택: (d) convert 트랜잭션 적재

**convert 트랜잭션이 `minsu` 생성 원장을 `pending`으로 함께 쓰고, `minsu` scheduler가 원장을
직접 claim한다.**

`signal`은 이미 `minsu`에 의존하고
(`SignalActionServiceImpl.generateDraft`가 `SignalTaskDraftInput`을 직접 조립한다), 그 입력을
그대로 `minsu`의 적재 API에 넘기면 된다. 적재 방향은 오늘과 같은 `signal → minsu`다.

- task_id는 적재 시점에 이미 있다.
- watermark·시드·cutover가 필요 없다.
- 원장 행이 응답 커밋 전에 존재하므로 `draft_status`를 원장에서 그대로 읽는다.
- 비동기 대상 여부가 원장 행의 존재로 durable하게 남는다. feature flag 해석에 의존하지 않아
  rollout·rollback 뒤에도 과거 task의 의미가 바뀌지 않는다.
- `pending` 원장 자체가 프로세스 재시작 복구 진입점이다.

원장 적재는 convert 트랜잭션의 일부이므로, 롤백되면 원장도 함께 사라진다. 커밋 전에 생성이
시작되는 일은 없다. scheduler는 커밋된 `pending` 행만 본다.

**이 선택은 fail-closed다.** 원장 insert가 실패하면 convert 전체가 롤백되어 public API가
실패한다. 기존 동기 계약은 LLM 실패를 fallback으로 흡수해 convert 가용성을 지켰지만, 비동기
활성 후에는 원장 테이블·제약·lock 문제가 convert 실패로 이어질 수 있다.

| 정책 | 얻는 것 | 잃는 것 |
| --- | --- | --- |
| fail-closed (채택) | 모든 비동기 intent가 durable. 상태 공백 없음 | 원장 장애가 convert API 장애가 됨 |
| fail-open | convert 가용성 유지 | 그 task는 풍부화되지 않고 원장도 없어 조용히 `ready` |

fail-closed를 택한 이유는 fail-open의 실패가 **보이지 않기** 때문이다. 원장이 없으면 `ready`로
응답하므로 앱도 사용자도 서버도 무엇이 빠졌는지 알 수 없고, 원장 장애가 길어지면 그동안의
task 전부가 조용히 풍부화되지 않는다. convert 실패는 최소한 드러나고 재시도가 replay로
흡수된다.

대신 원장 insert 실패율을 지표와 경보로 두고(9.3절), 구현 티켓의 검증 기준에 포함한다.

기존 outbox event(`signal.converted_to_task`, `task.created`)는 그대로 둔다. worker의 projection
소비 계약(ADR-0008)은 이 설계와 무관하며 바뀌지 않는다.

`notification`의 lease·claim token·백오프·at-least-once 패턴은 여전히 그대로 따른다. 달라지는
것은 작업이 원장에 들어오는 경로뿐이다.

| 요소 | notification | minsu(이 설계) |
| --- | --- | --- |
| 작업 유입 | outbox 소비 후 materialize | convert 트랜잭션이 직접 적재 |
| watermark | `NotificationConsumerOffset` | 필요 없음 |
| 작업 원장 | `push_deliveries` | `minsu` 생성 원장 |
| claim | `claim_token` + processing lease 30s | 동일 (8.4절) |
| 재시도 | 백오프 1s / 5s / 30s, `MAX_ATTEMPTS` | 동일 형태, 값은 구현 티켓 |
| 보장 | at-least-once | at-least-once |

### 5.4 공개 계약 변경: 동기 호출을 요청 경로에서 제거

**비동기가 활성이면 convert 요청 경로에서 LLM을 호출하지 않는다.** 3절이 지적한 지연 문제가
해결되는 근거가 이것이고, 이 설계의 목적 자체다.

그런데 현재 공개 계약 `SignalTaskDraftGenerator.generate`는 이를 표현할 수 없다.
`DefaultSignalTaskDraftGenerator.generate`는 비활성·설정 무효·입력 부족이 아닌 한 **항상**
provider를 호출한다. 비동기 활성 상태에서 고정 fallback을 얻으려고 이 메서드를 그대로 부르면
8초 예산이 응답 경로에 그대로 남는다.

따라서 `minsu` 공개 계약에 두 진입점을 둔다. 활성 여부 판정은 `minsu`가 소유하고 `signal`은
두 번 호출하기만 한다.

| 진입점 | 호출 위치 | 비동기 활성 | 비동기 비활성 |
| --- | --- | --- | --- |
| draft 확보 | 쓰기 트랜잭션 **밖** | LLM 미호출, 고정 fallback 반환 | 현재와 동일하게 LLM 호출 |
| 원장 적재 | 쓰기 트랜잭션 **안** | `pending` 행 적재 | 아무것도 하지 않음 |

두 진입점을 나누는 이유는 트랜잭션 경계가 다르기 때문이다. 동기 모드의 LLM 호출은 지금처럼
쓰기 트랜잭션 밖에 있어야 하고(느린 네트워크가 DB connection을 점유하지 않도록), 원장 적재는
`tasks`·`signal_actions`와 원자적이어야 하므로 트랜잭션 안이어야 한다. 하나로 합치면 둘 중
하나를 포기하게 된다.

기존 `generate` 시그니처를 그대로 둘지, draft 확보 진입점으로 흡수할지는 구현 티켓에서 정한다.
어느 쪽이든 `signal`은 활성 여부를 알지 못하고 `minsu`가 판정을 소유한다는 점은 같다. 9.2절의
"`disabled`면 적재하지 않는다"도 같은 원칙이다.

### 5.5 생성 입력의 시점

원장에 **convert 시점의 `SignalTaskDraftInput` snapshot을 저장한다.**

실행 시점에 Signal을 다시 읽는 방식(hydrate)은 `minsu → signal` 의존을 새로 만들고, Signal이
그 사이 수정·삭제되면 사용자가 convert를 누른 시점과 다른 근거로 draft가 만들어진다. 동기
경로는 convert 시점 입력을 쓰므로 snapshot이 현재 의미를 그대로 보존한다.

대가는 Signal의 description·impact·evidence 텍스트가 원장에 복제된다는 점이다. 같은 DB·같은
workspace 데이터 안의 복제다.

크기는 evidence가 아니라 **description과 impact가 좌우한다.** evidence는 이미 10건·필드당
30자 상한이 걸려 있어 약 900자로 묶이지만, `signals.description`은 `TEXT NOT NULL`,
`impact`는 `TEXT`로 스키마상 상한이 없다(`V20260707100000__create_signals.sql`). worker가
생산하는 요약이라 실제 값은 짧을 것으로 보지만 설계가 기대는 근거는 아니다.

다음을 구현 티켓에서 정한다.

- 원장 적재 시 description·impact에 자체 상한을 둘지, 둔다면 그 값
- 원장이 terminal 상태로 닫힌 뒤 snapshot을 비우는 보존 정책
- 원문·URL·작성자를 담지 않는다는 동기 설계 6절의 입력 제외 목록이 원장에도 그대로 적용됨

## 6. 생성 원장 소유권

**`minsu`가 별도 생성 원장 테이블을 소유하고, `tasks`에는 확정된 결과만 반영한다.**

`tasks`에 상태 컬럼을 얹는 안은 채택하지 않았다. `tasks`는 사용자가 `TaskEditor.update`로
편집하는 테이블이라 AI 갱신과 사용자 수정이 같은 row에서 경합하고, 원장을 `tasks`에 두면
생성 상태의 소유가 `project`로 넘어가 모듈 경계가 흐려진다.

원장은 `notification`의 `push_deliveries`와 같은 성격이다.

| 항목 | 내용 |
| --- | --- |
| 소유 모듈 | `minsu` |
| 적재 시점 | convert 트랜잭션 (5.3절) |
| 멱등 키 | `task_id` UNIQUE. convert 1건당 task 1건이 이미 `signal_actions UNIQUE(signal_id)`로 보장된다 |
| 보유 데이터 | `task_id`, 입력 snapshot(5.5절), 상태, completion reason, 시도 횟수, claim token, lease |
| 상태 | `pending` → `processing`(claim) → `completed`(reason 별도, 8.3절) |
| 재시도 | 시도 횟수와 다음 시도 시각을 원장이 소유 |
| 유실 복구 | lease 만료분과 재시작 후 남은 `pending`을 매 주기 회수 |

`tasks`에는 생성이 성공한 시점에만 title/role/priority를 반영한다. 어떤 이유로 끝나든
`tasks`는 convert 시점의 고정 fallback draft를 최소한으로 갖는다. 따라서 **`tasks`는 항상
유효한 draft를 갖는다**는 현재 성질이 비동기 전환 후에도 보존된다.

세부 컬럼과 Flyway 파일은 구현 티켓에서 확정한다. prod 반영은 공유 운영 스키마를 레거시가
단일 소유하므로(`docs/rules/persistence.md`) `momens-api` 마이그레이션 PR로 추가한다.

### 6.1 실행 흐름과 모듈 경계

```text
convert-to-task (동기)
  → SignalReader로 Signal·evidence 조회, SignalTaskDraftInput 조립  (signal, 현재와 동일)
  → minsu에서 draft 확보 (활성이면 LLM 미호출 고정 fallback, 5.4절)  (signal → minsu)
  → SignalActionExecutor.convert 트랜잭션
      tasks insert
      signal_actions insert
      minsu 생성 원장 insert (pending, 입력 snapshot 포함)          (signal → minsu)
      outbox_events insert (signal.converted_to_task, task.created)
  → commit → draft_status를 원장에서 읽어 응답

minsu scheduler (비동기)
  → pending 또는 lease 만료 원장 claim (claim token + lease commit)
  → 트랜잭션 밖에서 LlmClient.generate
  → 성공: claim token 조건으로 tasks CAS 반영 + 원장 completed 를 한 트랜잭션에    (minsu → project)
  → retryable 실패: 백오프 후 재시도 예약
  → terminal 실패: 원장 completed + reason (tasks는 고정 fallback 유지)

task 상세 조회
  → project에서 task 조회 + minsu에서 draft_status 조회               (mobile → minsu)
```

의존은 셋이고 그중 **둘이 새로 생긴다.**

| 의존 | 용도 | 상태 |
| --- | --- | --- |
| `signal → minsu` | 원장 적재, draft 확보 | 오늘 이미 존재 |
| `minsu → project` | 생성 결과를 `tasks`에 반영 | **신규** |
| `mobile → minsu` | task 상세의 `draft_status` 읽기 | **신규** |

`mobile → minsu`가 필요한 이유는 7.2절이 task 상세 조회에도 `draft_status`를 노출하기
때문이다. task 상세 경로는 `TaskController` → `ProjectTaskService` → `project`이고
`modules/mobile/build.gradle`에는 현재 `minsu`가 없다. convert 응답 경로는 `signal`이 원장을
읽어 `SignalActionResult`에 실으면 되므로 기존 의존으로 충분하지만, task 상세는 그렇지 않다.

`project → minsu`로 해결하려는 시도는 **금지한다.** `minsu → project`와 순환이 되어
`ApplicationModules.verify()`가 실패한다. 상태를 읽는 쪽은 조합 표면인 `mobile`이어야 한다.

`minsu`가 `signal`·`mobile`에 의존하지 않는다는 규칙은 그대로 유지된다. `mobile → minsu`는
반대 방향이라 이 규칙과 무관하고, 입력을 convert 시점 snapshot으로 받으므로(5.5절) `signal`을
참조할 이유도 없다.

`project`와 `minsu`는 서로를 모르는 관계에서 `minsu → project` 단방향으로 바뀐다. 이 제약은
동기 설계 4절("`minsu`는 `signal`, `project`, `mobile`에 의존하지 않는다")과 ADR-0014가 함께
전제한 것으로, 첫 슬라이스를 작게 유지하기 위한 조건이었고 비동기 전환으로 전제가 바뀌었다.
이 변경은 ADR-0014를 supersede 하는 [ADR-0015](../adr/0015-minsu-async-task-draft-generation.md)가
확정하며, `minsu → project` 참조 범위는 draft 반영 한 가지로 제한한다.

`signal`이 비동기 실행까지 조립하는 안은 채택하지 않았다. 생성 원장 소유가 `signal`로 넘어가
6절 결정과 어긋나고, Signal action의 멱등·충돌 정책을 담당하는 모듈이 LLM 작업의 claim·
재시도·백오프 운영까지 떠안게 된다. `signal`이 하는 일은 오늘처럼 입력을 조립해 `minsu`에
넘기는 것까지다.

## 7. 상태 전이와 모바일 계약

### 7.1 상태 전이

원장 상태와 API가 노출하는 상태를 분리한다. 상태는 셋이고, 종료 사유는 상태가 아니라
`completion_reason`으로 따로 갖는다.

| 원장 상태 | 의미 | API 노출 |
| --- | --- | --- |
| `pending` | 적재됨, 생성 대기 | `generating` |
| `processing` | claim 보유, 생성 중 | `generating` |
| `completed` | 종료. 사유는 `completion_reason` | `ready` |

| `completion_reason` | 의미 | `tasks` 반영 |
| --- | --- | --- |
| `generated` | 모델 draft 반영 | 반영됨 |
| `user_edited` | 사용자가 먼저 편집해 결과 폐기 (8.1절) | 사용자 편집 유지 |
| `operationally_closed` | 운영 판단으로 수동 종료 (11.1절) | 고정 fallback 유지 |
| `deadline_exceeded` | 원장 나이 상한 초과 (8.6절) | 고정 fallback 유지 |
| `insufficient_context` | 입력 부족으로 재시도 없이 종료 | 고정 fallback 유지 |
| `invalid_config` | 설정 무효로 종료 | 고정 fallback 유지 |
| `retry_exhausted` | 재시도 상한 도달 | 고정 fallback 유지 |

종료 사유를 상태로 만들지 않은 이유는 `user_edited`가 성공도 실패도 아니기 때문이다. 모델은
정상 생성했지만 반영하지 않았으므로 `succeeded`가 아니고, 재시도 상한에 도달한 실패도 아니다.
상태를 늘려 표현하면 "종료됐는가"를 판정할 때마다 상태 목록을 열거해야 한다. 상태는 진행
여부만 표현하고 사유는 별도 컬럼으로 두는 편이 API 매핑과 운영 판단 모두 단순하다.

**API는 `generating`과 `ready` 두 값만 노출한다.** 모든 `completed`는 사유와 무관하게 `ready`로
매핑한다. 동기 설계 5절의 "fallback 여부와 이유는 public API 응답에 넣지 않고 내부 관측으로
남긴다"를 그대로 유지하기 위해서다. 앱에게 필요한 정보는 "이 title이 앞으로 더 바뀌는가"
하나이고, 그 점에서 모든 종료 사유는 같다. 사유는 원장과 관측에만 남는다.

원장 행이 없는 task(비동기 도입 이전에 만들어졌거나 비동기 비활성 상태에서 convert된 task,
그리고 Signal을 거치지 않은 일반 task)는 `ready`로 응답한다. 원장 행의 존재 자체가 비동기
대상 여부를 durable하게 표현하므로, feature flag의 현재 값으로 과거 task의 상태를 해석하지
않는다. rollout·rollback 뒤에도 판정이 달라지지 않는다.

### 7.2 모바일 계약 변경

`ConvertToTaskResponse`에 draft 생성 상태를 추가한다. unknown field를 허용하는 클라이언트에는
하위 호환 확장이다. 앱의 decoder가 unknown field를 허용하지 않으면 필드 추가 시점에 앱도 함께
배포해야 하므로, 이 전제를 배포 순서와 함께 모바일에 알린다.

```text
{
  "task":   { "id", "title", "status", "draft_status": "generating" | "ready" },
  "signal": { "id", "action" }
}
```

- 원장 행이 없으면(비동기 비활성 포함) 항상 `ready`로 응답한다. 동기 경로와 계약이 갈라지지
  않는다.
- 원장 행이 convert 트랜잭션에서 함께 커밋되므로 신규 convert 응답과 직후 재조회 모두 원장을
  읽어 상태를 정한다. 응답 시점에 행이 없는 구간이 존재하지 않는다.
- 멱등 replay 응답도 같은 규칙을 따른다. 이미 생성이 끝났으면 `ready`, 아직 진행 중이면
  `generating`이다.
- 앱은 `generating`일 때 로딩 표시를 할지, fallback title을 그대로 보여주고 조용히 갱신할지
  스스로 정한다. 서버는 사실만 알리고 UX 판단을 앱에 남긴다.
- task 상세 조회 응답에도 같은 값을 노출해 앱이 재조회로 종료를 확인할 수 있게 한다.
- push 통지는 1차 범위에서 제외한다. push는 토큰 무효·알림 권한 거부·앱 종료로 수신 보장이
  약해 재조회 경로를 대체하지 못하고 그 위에 얹는 최적화이므로, 재조회가 자리 잡은 뒤 별도로
  판단한다.

이 계약은 서버가 확정하고 모바일에 알린다. 앱이 `generating`을 어떻게 표시할지, 재조회를
언제 어떤 간격으로 할지는 앱이 정한다. 서버가 보장하는 것은 다음 셋이다.

- `draft_status`는 반드시 종료 상태(`ready`)에 도달한다(8.6절).
- `ready`와 함께 반환한 `task.title`은 최종 값이다. `ready`를 보고 재조회를 멈춰도 갱신을
  놓치지 않는다(7.3절).
- `generating` 동안에도 `task.title`은 항상 유효한 draft다. 앱이 로딩을 표시하지 않고 그대로
  렌더해도 깨지지 않는다.

### 7.3 응답 조립의 읽기 순서

`tasks`와 원장은 서로 다른 모듈이 각각 읽는다. PostgreSQL 기본 격리 수준
`READ COMMITTED`에서는 두 SELECT가 다른 snapshot을 볼 수 있어, 순서를 정하지 않으면 다음이
가능하다.

```text
1. task를 읽는다 → fallback title
2. scheduler가 AI title + completed 를 원자적으로 커밋
3. 원장을 읽는다 → ready
4. 앱은 fallback title + ready 를 받고 재조회를 멈춘다
```

서버는 AI 결과를 정상 저장했는데 앱은 fallback title을 영구히 표시한다. 8.3절의 쓰기 원자성은
이 문제를 막지 못한다. 쓰기가 원자적이어도 **읽기가 두 번**이기 때문이다.

따라서 읽기 순서를 계약으로 고정한다.

**신규 convert 응답** — 원장을 적재했으면 상태를 다시 읽지 않고 `generating`을 반환한다.
응답에 담는 title은 방금 만든 fallback이므로 이 조합은 항상 정합하다. 적재하지 않았으면
(비동기 비활성) `ready`다.

**replay 응답과 task 상세** — **원장 상태를 먼저 읽고 그다음 task를 읽는다.**

- `ready`를 먼저 봤다면 그 뒤 읽는 task는 반영이 끝난 상태이거나 그보다 더 최신이다. 따라서
  `ready`와 함께 반환하는 title은 최종 값이다.
- `generating`을 본 뒤 scheduler가 끝나면 AI title + `generating`이 될 수 있다. 이 조합은
  안전하다. 앱이 한 번 더 재조회할 뿐 결과를 놓치지 않는다.

역순(`task` → 원장)은 위 시나리오를 그대로 허용하므로 금지한다. 구현 티켓에는 scheduler 완료가
응답 조립 사이에 끼어도 `fallback title + ready`가 나오지 않는 동시성 테스트를 포함한다.

## 8. 경합·멱등성·실패 복구

### 8.1 사용자 수정과 AI 결과의 경합

**사용자 수정이 항상 우선한다.** convert 직후 사용자가 `TaskEditor.update`로 제목을 고쳤는데
뒤늦게 도착한 AI 결과가 이를 덮으면 사용자가 명시적으로 한 편집이 사라진다.

판정은 **조건부 UPDATE(compare-and-set)** 로 한다. 반영 시점에 `tasks`의 title/role/priority가
여전히 convert 시점의 고정 fallback 값과 같을 때만 갱신한다. 값이 달라졌으면 사용자가 이미
편집한 것이므로 AI 결과를 폐기하고 원장을 닫는다.

`updated_at` 비교는 쓰지 않는다. 체크리스트 토글처럼 draft와 무관한 갱신에도 걸려, 실제 편집이
없는데 결과를 버린다.

두 방식의 오차 방향은 다르다.

- 조건부 UPDATE는 **편집이 없는데 버리는 오탐이 없다.** 값이 fallback 그대로면 title·role·
  priority가 실질적으로 바뀌지 않은 것이 확실하다.
- 대신 **편집을 놓치는 미탐이 있다.** title/role/priority가 결과적으로 fallback과 같으면
  편집으로 감지되지 않아 AI 결과가 반영된다.

미탐은 드물지 않다. `UpdateTaskCommand`는 "수정 화면이 저장할 때 편집 상태 전체를 보내므로
title, role, priority, status, purpose는 항상 채워진 값으로 넘어온다". 즉 사용자가 purpose나
완료기준만 고쳐 저장해도 title/role/priority는 fallback 값 그대로 다시 쓰이고, 그 뒤 도착한
AI 결과가 title을 덮는다.

그래도 미탐을 허용한다. 이 경우 사용자는 title을 건드리지 않았으므로 편집 의도가 사라지지
않고, 반영되는 AI 결과 역시 유효한 draft다. 반대로 오탐은 실제 편집을 덮거나 생성 결과를
이유 없이 버리므로 피해가 실질적이다. 오탐 0을 택했다.

**CAS는 세 필드를 함께 본다.** title/role/priority 중 하나라도 달라지면 **draft 전체를 폐기한다.** 필드별로 나눠 일치하는
것만 반영하지 않는다.

부분 반영은 사용자가 보는 draft를 사람과 모델의 혼합물로 만든다. 예를 들어 사용자가 priority만
높였는데 AI가 만든 title이 반영되면, 그 title은 사용자가 판단한 priority를 전제로 쓰인 것이
아니다. 동기 설계 9절이 "role 또는 priority가 무효면 title만 쓰지 않고 draft 전체를 고정
fallback으로 바꾼다"며 값을 섞지 않기로 한 것과 같은 원칙이다.

대가는 사용자가 priority 하나만 바꿔도 AI title까지 버려진다는 점이다. 이를 감수한다. 반대
방향의 혼합물이 사용자에게 설명하기 더 어렵다.

### 8.2 실행 fencing

claim은 lease 만료 시각만으로 충분하지 않다. 동기 경로에서 확인했듯 SDK timeout이 ADC 탐색·
client 생성·token refresh 전체를 덮지 않으므로(4절), lease보다 오래 걸리는 호출이 가능하다.

```text
worker A가 claim → 모델 호출
  → 호출이 길어져 lease 만료
  → worker B가 재claim → 호출
  → worker A가 뒤늦게 성공 반환
  → fencing이 없으면 A와 B가 모두 결과를 기록
```

`notification`의 `PushDelivery`가 `claim_token`을 저장하고 결과 기록 시 `isClaimedBy`로 소유권을
재검증하는 것과 같은 규칙을 correctness requirement로 둔다.

- lease 만료 판정은 DB 시계를 기준으로 한다. 앱 서버 간 시계 차이의 영향을 받지 않는다.
- claim마다 새 token을 발급하고 원장에 저장한다.
- 결과 기록·재시도 예약·terminal 전이는 **모두 claim token이 일치할 때만** 수행한다. token이
  다르면 그 결과는 stale이므로 버리고 관측에만 남긴다.
- 장시간 호출 중 lease 갱신 여부는 구현 티켓에서 정한다. 갱신하지 않으면 lease를 비동기
  timeout보다 충분히 크게 잡아야 한다.

### 8.3 반영의 원자성

**`tasks` CAS 반영과 원장 terminal 전이는 한 트랜잭션이어야 한다.** 나누면 다음이 가능하다.

```text
1. tasks CAS update commit
2. 프로세스 종료
3. 원장은 processing으로 남음
4. 재시도에서 CAS가 0건 (이미 모델 값으로 바뀌었으므로)
5. 사용자 편집으로 오판해 user_edited로 종료
```

결과 자체는 이미 반영됐으므로 사용자 피해는 없지만 종료 사유가 틀리고, 그 사유는 운영 판단에
쓰인다(9.2절). claim token 조건 아래 두 변경을 한 트랜잭션에서 커밋한다.

1. `tasks` 조건부 UPDATE
2. 원장 `completed` + `completion_reason` 기록

### 8.4 멱등성

- 원장 적재는 convert 트랜잭션의 일부라 정확히 한 번이다. 트랜잭션이 롤백되면 원장도 없다.
  재시도로 들어온 convert는 `signal_actions UNIQUE(signal_id)`에 걸려 replay가 되므로 원장을
  다시 적재하지 않는다. `task_id` UNIQUE가 이중 방어로 남는다.
- **결과 기록**은 claim token으로 직렬화한다. token을 보유한 실행만 기록할 수 있다.
  이는 exactly-once **기록**이지 exactly-once **실행**이 아니다. lease가 만료되면 8.2절처럼
  두 worker가 동시에 모델을 호출할 수 있고, at-least-once와 만료 lease를 택한 이상 복구
  과정의 중복 실행은 정상 동작이다. 중복 실행의 비용은 관측으로 본다(9.3절).
- 반영은 조건부 UPDATE라 두 번 적용해도 결과가 같다. 첫 번째가 값을 바꾸면 두 번째는 조건에
  걸려 아무것도 하지 않는다.

### 8.5 실패 복구

| 상황 | 복구 |
| --- | --- |
| convert 커밋 후 scheduler 실행 전 종료 | `pending` 원장이 남아 다음 주기가 집어간다 |
| claim 보유 중 프로세스 종료 | lease 만료 후 다음 주기가 회수해 재시도한다 |
| 모델 호출 실패·timeout | 백오프 후 재시도, 상한 도달 시 `retry_exhausted` |
| stale worker의 늦은 성공 | claim token 불일치로 기록이 거부된다 (8.2절) |
| 반영 트랜잭션 도중 종료 | 커밋되지 않아 원장이 `processing`으로 남고 lease 만료 후 재시도한다 |

어떤 경로로 끝나든 `tasks`는 convert 시점부터 항상 유효한 draft를 갖는다. 비동기 생성이
전부 실패해도 사용자가 보는 것은 현재 동기 경로의 fallback 결과와 같다. **비동기 전환은
품질의 상한만 올리고 하한은 내리지 않는다.**

### 8.6 `generating`의 상한

위 복구 경로는 모두 scheduler가 돌고 있다는 전제 위에 있다. scheduler 자체가 멈추거나
(11.1절) 회수 루프에 버그가 있으면 원장이 terminal로 갈 방법이 없고, 앱은 `generating`을
무기한 본다.

따라서 **원장 나이 상한(deadline)** 을 둔다. 적재 시각 기준으로 일정 시간이 지나면 그 작업은
종료된 것으로 본다. 상한은 재시도 백오프 총합보다 충분히 크게 잡고 구체적 값은 구현 티켓에서
정한다.

중요한 것은 **이 상한을 누가 강제하는가**다. scheduler가 상한을 처리하게 두면 순환이 된다.
scheduler가 멈춘 상황을 막으려고 둔 장치를 그 scheduler가 실행하기 때문이다. 나이 상한은 행의
속성일 뿐 실행 주체가 아니다.

그래서 상한을 **읽기 시점에 투영한다.**

```text
draft_status =
  원장 행 없음                             → ready
  completed                                → ready
  pending/processing 이고 now() > deadline → ready   (투영)
  그 외                                    → generating
```

`now()`는 원장과 같은 DB 시계다. 이 판정은 조회 경로에서 비교 하나로 끝나므로 scheduler가
멈춰 있어도, 설정을 꺼서 rollback해도 성립한다. 앱은 어떤 경우에도 `generating`에 무기한
갇히지 않는다.

deadline을 넘긴 뒤 뒤늦게 돌아온 scheduler가 결과를 반영하면 앱에 이미 `ready`로 알린 값이
바뀐다. 이를 막기 위해 **반영 CAS에도 deadline 조건을 넣는다.** deadline이 지난 작업은 claim
token이 맞아도 반영하지 않고 원장을 `deadline_exceeded`로 닫는다. 8.2절의 claim token fencing과
같은 성격의 조건이 하나 더 붙는 것이다.

원장의 물리적 정리(상태를 `completed`로 바꾸고 snapshot을 비우는 것)는 scheduler가 나중에
해도 된다. public 계약은 읽기 투영으로 이미 닫혀 있다.

이 보장의 범위는 **원장 행이 있는 task**다. `minsu` 자체가 없는 이전 바이너리로 rollback하면
응답에 `draft_status` 필드가 없으므로 앱은 이전 계약을 본다. 설정만 끄는 rollback과 바이너리
rollback의 차이는 11.1절에 정리한다.

상한이 발동한다는 것 자체가 이상 신호이므로 9.3절의 "가장 오래된 `pending`의 age",
`deadline_exceeded` counter와 함께 경보 대상으로 둔다.

## 9. timeout·retry·관측

### 9.1 비동기 경로의 timeout

동기 경로의 8초(MOM-0806)는 **그대로 둔다**. 이 값은 원탭 action의 사용자 체감을 기준으로
정했고, 비동기가 비활성일 때 convert가 타는 경로가 지금 그대로이기 때문이다. 비동기가
활성이면 요청 경로에서 LLM을 부르지 않으므로(5.4절) 이 값은 그 경로에 적용되지 않는다.

비동기 경로는 사용자가 기다리지 않으므로 별도 설정 키로 분리하고 더 큰 값을 갖는다. 구체적
값은 dev 재측정 뒤 확정한다. 동기 경로의 8초를 비동기 값으로 재사용하지 않는다. 두 경로가
같은 키를 공유하면 한쪽을 늘릴 때 다른 쪽의 사용자 체감이 함께 나빠진다.

재시도는 비동기 경로에서 **도입한다.** 동기 경로가 재시도를 두지 않은 이유는 사용자가 그
시간을 기다리기 때문이었고, 비동기에서는 그 제약이 없다. 백오프와 상한은 `push_deliveries`의
형태(고정 백오프 목록 + `MAX_ATTEMPTS`)를 따르되 값은 구현 티켓에서 정한다.

### 9.2 재시도 판정과 내부 실행 계약

공개 `SignalTaskDraftGenerator.generate`는 실패를 전파하지 않고 항상 유효한 `TaskDraft`를
반환한다. 반환값만 보면 그 draft가 모델 결과인지, 재시도하면 될 일시 실패인지, 재시도해도
같을 확정 fallback인지 구분할 수 없다. **이 계약만으로는 재시도를 구현할 수 없다.**

공개 계약은 그대로 둔다. 동기 경로의 호출자(`signal`)에게는 지금 형태가 정확하고, 비동기
consumer는 `minsu` 내부에 있으므로 내부 실행 계약을 따로 두면 된다. 내부 실행은 draft와 함께
outcome·재시도 가능 여부를 반환하고, 기존 내부 `GenerationOutcome`을 그대로 재사용한다.

| outcome | 판정 | 근거 |
| --- | --- | --- |
| `generated` | 성공 반영 | — |
| `generated_title_fallback` / `generated_truncated` | 성공 반영 | 모델 결과이며 정규화만 적용됨 |
| `timeout` | retryable | 일시적 지연일 수 있다 |
| `provider_error` | retryable | ADC·client 생성 실패와 quota 초과를 포함하며 복구 가능하다 |
| `invalid_response` | retryable | 같은 입력에도 모델 출력이 달라질 수 있다 |
| `invalid_output` | retryable | 위와 같다 |
| `insufficient_context` | terminal | 입력이 그대로이므로 재시도해도 같다 |
| `invalid_config` | terminal | 설정을 고치기 전에는 같다. 설정 수정은 배포 사건이므로 재시도로 기다리지 않는다 |
| `disabled` | 적재 안 함 | 비활성이면 원장을 만들지 않는다 (11절) |

`invalid_response`와 `invalid_output`을 retryable로 둔 이유는 structured output 위반이 결정적
오류가 아니기 때문이다. 다만 같은 입력으로 반복 실패하면 프롬프트·스키마 문제이므로
`retry_exhausted` 비율을 outcome별로 관측해 구분한다.

`provider_error`는 성격이 다른 실패를 한 값으로 묶는다. 일시 네트워크 오류나 일부 429·5xx는
재시도로 복구되지만, 잘못된 ADC·IAM 403·존재하지 않는 project/location은 설정을 고치기 전에는
같고, 일일 quota 소진은 짧은 백오프로 복구되지 않는다. 기존 `GenerationOutcome.PROVIDER_ERROR`
하나로는 이를 구분할 수 없다.

**첫 구현에서는 구분하지 않고 모두 제한 재시도한다.** 재시도 횟수에 상한이 있어 정합성 문제는
없고, 비용은 복구 불가능한 실패에 대한 몇 번의 헛된 호출과 그만큼 늘어난 `generating` 시간이다.
어느 쪽이 실제로 흔한지는 dev 측정 전에는 알 수 없으므로 지금 세분화하지 않는다. 관측에서
`retry_exhausted` 중 `provider_error` 비중이 크면 그때 category를 나눈다.

`invalid_config`는 terminal이지만 원장을 닫을 뿐 task는 고정 fallback을 유지하므로 손실이
없다. 설정을 고친 뒤 과거 건을 다시 생성할지는 운영 판단이며 이 설계에 넣지 않는다.

### 9.3 관측

동기 경로의 `momens.minsu.llm.generate` observation과 outcome/fallback reason tag를 그대로
쓰고, 비동기 경로임을 구분하는 low-cardinality tag를 추가한다. 여기에 원장 운영 지표를
더한다.

진행 상태:

- 원장 상태별 건수(`pending`/`processing`/`completed`)
- **가장 오래된 `pending`의 age** — 큐가 밀리는지 보는 1차 지표
- **claim 이후 실제 모델 호출 시작까지의 queue delay**
- convert 커밋부터 반영까지의 end-to-end 지연

종료와 실패:

- `completion_reason`별 counter
- 재시도 횟수 분포와 outcome별 `retry_exhausted` 비율
- **lease 만료로 회수된 작업 수**
- **claim token 불일치로 무시된 stale 결과 수**
- **task 하나당 실제 provider 호출 수** — 복구 과정의 중복 실행 비용(8.4절)
- **deadline 투영으로 `ready`가 된 건수** — 0이 정상이며 경보 대상(8.6절)
- **원장 insert 실패율** — fail-closed 선택의 대가를 보는 지표(5.3절)

`retry_exhausted` 비율과 `user_edited` 건수는 제품 판단에 직접 쓰인다. 전자가 높으면 모델·
프롬프트 문제이고, 후자가 높으면 비동기 생성이 사용자보다 느려 가치가 없다는 신호다.

lease 만료 회수와 stale 결과 수는 함께 본다. 둘이 같이 오르면 lease가 실제 호출 시간보다
짧다는 뜻이므로 lease 갱신 도입 또는 lease 연장을 판단한다.

## 10. Agentic RAG 성립 여부

비동기 전환은 지연 예산의 상한을 **사용자 체감에서 원장 재시도 정책으로 옮긴다.** 8초라는
제약이 사라지므로 검색과 재검토를 반복하는 구조가 시간 측면에서는 성립한다.

다만 지금 성립하지 않는 것은 시간이 아니라 **재료**다. Agentic RAG는 반복 검색 대상이 있어야
하는데 `minsu`는 현재 retrieval에 연결돼 있지 않다. ADR-0014가 retrieval gRPC와 query
embedding을 명시적으로 제외했고, 검색 read-model은 `momens-retrieval`이 소유한다. 즉 지연
예산은 필요조건이었을 뿐 충분조건이 아니다.

정리하면 이렇다.

- 비동기 전환은 Agentic RAG의 **전제 조건**을 만든다. 이 티켓 범위는 여기까지다.
- 실제 도입은 `minsu → retrieval` 연동이 선행돼야 하며 별도 설계·티켓이 필요하다.
- 그 시점에 지연과 검색 품질의 타협점은 재시도 상한과 end-to-end 지연 관측값을 근거로
  다시 정한다. 지금은 근거가 될 데이터가 없다.

## 11. 단계적 전환

기존 동기 계약을 한 번에 끊지 않는다.

1. **동기 유지 + 원장 도입** — 비동기 생성을 기본 비활성으로 두고 원장·scheduler·관측만
   배포한다. 비활성이면 convert가 원장을 적재하지 않으므로 동작은 현재와 같다. 이 단계에서
   스케줄링 게이트를 먼저 정리한다(11.2절).
2. **dev 활성화** — dev에서 비동기를 켜고 end-to-end 지연, `retry_exhausted` 비율,
   `user_edited` 건수를 측정한다. 3구간 timeout 예산의 미측정 구간(4절)도 이때 함께 채운다.
3. **모바일 계약 확장** — `draft_status`를 응답에 추가한다. 원장 행이 없으면 항상 `ready`라
   앱은 단계 1·2에서도 안전하게 배포할 수 있다. 필드 추가 시점을 모바일에 미리 알린다.
4. **prod 활성화** — 데이터 레지던시 정책(동기 설계 11절)과 dev 재측정이 끝난 뒤 판단한다.

### 11.1 rollout·rollback과 혼재 배포

비활성이면 **원장을 적재하지 않는다.** 활성 pod가 적재한 행만 존재하므로 원장 행의 존재가
비동기 대상 여부를 durable하게 표현한다. 이 성질이 혼재 배포와 rollback을 단순하게 만든다.

- **롤링 중 혼재** — 활성 pod가 적재한 작업은 원장에 남고, 어느 pod의 scheduler가 집어가도
  된다. 비활성 pod가 처리한 convert는 원장이 없어 동기 경로와 동일하다. 두 경우 모두 결과가
  정의돼 있다.
- **설정 rollback(flag off) 후 남은 `pending`** — 기본은 **계속 처리한다.** 이미 적재된
  작업은 유효하고, 처리해도 `tasks`는 유효한 draft를 유지한다. scheduler까지 멈춰야 하는
  상황이면 별도 스위치로 멈추고 원장은 남긴다. 이때도 앱은 `generating`에 갇히지 않는다.
  deadline이 지나면 읽기 투영이 `ready`로 닫기 때문이다(8.6절). 남은 행을
  `operationally_closed`로 즉시 닫는 절차는 지표를 깨끗하게 유지하기 위한 것이며 구현
  티켓에서 명령이나 쿼리로 구체화한다. `invalid_config`를 재사용하지 않는다. 실제 설정 오류
  지표가 오염된다.
- **바이너리 rollback** — `minsu` 원장·조회가 없는 이전 배포로 되돌리면 응답에
  `draft_status` 필드 자체가 없다. 앱은 이전 계약을 보므로 `generating`에 갇힐 대상이 없다.
  원장 행은 DB에 남아 있다가 다시 배포하면 이어서 처리되거나 deadline으로 닫힌다.
- **활성화 시점 이전 task** — 원장이 없으므로 `ready`다. 소급 생성하지 않는다.

각 단계는 앞 단계로 되돌릴 수 있다. 설정을 끄면 convert는 동기 경로로 돌아가고 `tasks`는
여전히 유효한 draft를 갖는다.

### 11.2 스케줄링 게이트 정리

현재 `@EnableScheduling`은 `notification` 모듈이 소유하고
`momens.notification.push.enabled`에 걸려 있다. `NotificationSchedulingConfig`의 주석이
"다른 모듈에는 아직 `@Scheduled` 빈이 없어 이 모듈이 게이트를 소유한다"고 그 한시성을
명시한다.

이 상태로 `minsu` scheduler를 추가하면 **push가 꺼진 환경에서 조용히 동작하지 않는다.**
local·test 기본값이 push 비활성이고, 위 단계 1·2가 push 설정에 우연히 종속된다. 실패가
드러나지 않고 원장만 쌓이므로 특히 나쁘다.

`minsu`가 두 번째 `@Scheduled` 소유자가 되는 시점이므로 이때 게이트를 다시 정한다. 스케줄링
인프라 활성화를 `app` 또는 `common`으로 올리고 모듈별 실행 여부는 각자의 설정으로 판정하는
방향이 자연스럽다. 구체적 배치는 구현 티켓에서 정하되, **`minsu` scheduler가 push 설정과
무관하게 동작한다**는 것이 요구사항이다.

## 12. 후속 구현 티켓

이 문서는 설계만 확정한다. 구현은 다음으로 분리한다.

1. **원장·scheduler 기반** — `minsu` 생성 원장 테이블, convert 트랜잭션 적재 API, draft 확보
   진입점 분리(5.4절), claim token과 lease, 재시도 판정, 조건부 UPDATE 반영과 terminal 전이의
   원자성, 원장 나이 상한, 스케줄링 게이트 정리(11.2절), 관측. 기본 비활성.

   이 티켓은 `minsu`를 **무상태 모듈에서 영속성 소유 모듈로 바꾼다.** 현재
   `modules/minsu/build.gradle`에는 JPA·DB 의존이 전혀 없다(validation, actuator, jackson,
   google-genai). 모듈 의존 추가와 `common`의 영속성 기반 준수가 범위에 포함된다.

   `project`의 반영 API는 **호출자 트랜잭션에 참여해야 한다.** `minsu`가 트랜잭션을 열고 CAS
   반영과 원장 terminal 전이를 함께 커밋하기 때문이다(8.3절). 구체적으로는 다음을 지킨다.

   - 같은 transaction manager를 쓰고 기본 `PROPAGATION_REQUIRED`로 참여한다. 반영 API에
     `@Transactional`을 붙이는 것 자체는 문제가 아니다. 기본 전파가 이미 참여이고 현재
     `TaskEditorImpl.update`도 같은 방식이다.
   - `REQUIRES_NEW`, 별도 transaction manager, 명시적 조기 commit은 금지한다.
   - CAS가 0건이거나 원장의 token·deadline 조건 갱신이 0건이면 예외 없이 커밋하지 않고 전체를
     롤백한다. task만 바뀌고 원장이 남는 상태를 만들지 않는다.

2. **모바일 계약 확장** — `draft_status` 응답 필드와 `mobile → minsu` 읽기 의존, 7.3절의 읽기
   순서 계약. API 명세를 갱신하고 필드 추가를 모바일에 알린다. 앱 decoder가 unknown field를
   거부하면 서버 단독 선배포가 앱을 깨뜨리므로, **현재 앱 decoder의 unknown field 동작 확인과
   그에 따른 배포 순서 결정**을 이 티켓의 완료 조건에 포함한다.

3. **dev 측정** — end-to-end 지연, `retry_exhausted` 비율, `user_edited` 건수, 3구간 timeout
   미측정 구간.
4. **prod 마이그레이션** — `momens-api`에 원장 테이블 마이그레이션 PR.

ADR-0015가 이 PR에 함께 들어가므로 별도 ADR 티켓은 두지 않는다.

검증 기준은 각 티켓이 소유한다. 이 문서는 어떤 값이 근거를 갖춰야 하는지만 정한다.

## 13. 미확정으로 남는 것

- 비동기 경로의 timeout 값과 재시도 백오프·상한 — dev 재측정 후
- lease 길이와 장시간 호출 중 lease 갱신 여부 — 구현 티켓 (8.2절)
- 원장 나이 상한 값 — 구현 티켓 (8.6절)
- 원장 테이블의 세부 컬럼과 Flyway 파일명 — 구현 티켓
- 입력 snapshot의 description·impact 상한과 보존·삭제 정책 — 구현 티켓 (5.5절)
- 스케줄링 게이트를 올릴 위치 — 구현 티켓 (11.2절)
- 기존 `generate` 시그니처의 존치 여부 — 구현 티켓 (5.4절)
- scheduler 중단 시 남은 `pending`을 닫는 운영 절차 — 구현 티켓 (11.1절)
- prod 데이터 레지던시 정책 — 동기 설계 11절에서 이월된 미결
- `minsu → retrieval` 연동 — 별도 설계
