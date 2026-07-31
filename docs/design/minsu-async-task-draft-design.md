# MOM-0810 Minsu 풍부한 task draft 비동기 생성 설계

작성일: 2026-08-01

상태: 확정 설계 (구현 전). 모바일 계약(7.2절)은 합의 대상이다.

관련 결정: [ADR-0011](../adr/0011-signal-evidence-and-task-draft-contract.md),
[ADR-0014](../adr/0014-minsu-task-draft-module-and-llm-boundary.md),
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

**(c) outbox 소비** — convert 트랜잭션은 **이미** `signal.converted_to_task`와 `task.created`를
`outbox_events`에 원자적으로 쓰고 있다. 새 trigger 인프라 없이 기존 event를 소비하면 된다.
`OutboxEventReader.readAfter(afterId, safetyLag, limit)`가 BIGSERIAL commit 순서 역전을
prefix-cap으로 막는 로직까지 이미 구현돼 있다.

### 5.2 사내 선례

(c)는 `notification` 모듈이 이미 같은 형태로 구현해 운영 중인 패턴이다.

| 요소 | notification 구현 |
| --- | --- |
| watermark | `NotificationConsumerOffset` |
| polling | `NotificationPushScheduler` (`fixedDelay=1s`) |
| materialize | `SignalCreatedDeliveryMaterializer` |
| 작업 원장 | `push_deliveries` (claim + processing lease 30s) |
| 재시도 | 백오프 1s / 5s / 30s, `MAX_ATTEMPTS` 상한 |
| 보장 | at-least-once (`signal-push-demo-design.md` 10.4절) |

ADR-0008은 outbox 소비를 worker 소유로 정했고 ADR-0009가 notification 소비를 api-server로
이관했다. minsu가 세 번째 소비자가 되는 것은 이 선례의 연장이며, 자기 모듈의 watermark를
소유하는 규약(`OutboxEventReader`가 조회만 제공하고 소비 상태는 consumer가 관리)도 그대로
따를 수 있다.

**권고: (c) outbox 소비.** 새 인프라가 없고, 커밋 원자성이 이미 보장되며, 재시작 복구·
재시도·관측 패턴을 notification에서 그대로 가져올 수 있다. (b)는 event registry 도입이라는
추가 비용 대비 이점이 없고, (a)는 유실 위험 때문에 배제한다.

## 6. 생성 원장 소유권

**`minsu`가 별도 생성 원장 테이블을 소유하고, `tasks`에는 확정된 결과만 반영한다.**

`tasks`에 상태 컬럼을 얹는 안은 채택하지 않았다. `tasks`는 사용자가 `TaskEditor.update`로
편집하는 테이블이라 AI 갱신과 사용자 수정이 같은 row에서 경합하고, 원장을 `tasks`에 두면
생성 상태의 소유가 `project`로 넘어가 모듈 경계가 흐려진다.

원장은 `notification`의 `push_deliveries`와 같은 성격이다.

| 항목 | 내용 |
| --- | --- |
| 소유 모듈 | `minsu` |
| 멱등 키 | `task_id` UNIQUE. convert 1건당 task 1건이 이미 `signal_actions UNIQUE(signal_id)`로 보장된다 |
| 상태 | `pending` → `processing`(lease) → `succeeded` / `exhausted` |
| 재시도 | 시도 횟수와 다음 시도 시각을 원장이 소유 |
| 유실 복구 | lease 만료분과 재시작 후 남은 `pending`을 매 주기 회수 |

`tasks`에는 생성이 성공한 시점에만 title/role/priority를 반영한다. 실패가 상한에 도달하면
원장을 `exhausted`로 닫고 `tasks`는 convert 시점의 고정 fallback draft를 그대로 유지한다.
따라서 **`tasks`는 항상 유효한 draft를 갖는다**는 현재 성질이 비동기 전환 후에도 보존된다.

세부 컬럼과 Flyway 파일은 구현 티켓에서 확정한다. prod 반영은 공유 운영 스키마를 레거시가
단일 소유하므로(`docs/rules/persistence.md`) `momens-api` 마이그레이션 PR로 추가한다.

### 6.1 소비·반영 주체와 모듈 경계

**`minsu`가 outbox 소비, 생성 원장, `tasks` 반영을 모두 소유한다.** `notification`이 outbox
소비부터 FCM 발송까지 자족적으로 갖는 것과 같은 형태다.

```text
convert-to-task (동기)
  → SignalActionExecutor.convert
      → tasks + signal_actions + outbox_events(signal.converted_to_task, task.created) 원자 저장

minsu (비동기)
  → OutboxEventReader.readAfter        (signal.converted_to_task 소비, 자기 watermark 소유)
  → 생성 원장 materialize (pending)
  → claim(lease) → LlmClient.generate
  → 성공: project 공개 API로 tasks 반영 + 원장 succeeded
  → 실패: 백오프 후 재시도, 상한 도달 시 원장 exhausted (tasks는 고정 fallback 유지)
```

이 배치는 `minsu → project` 단방향 의존을 새로 만든다. `project`는 `minsu`를 모르므로
순환은 아니지만, ADR-0014의 "`minsu`는 `signal`, `project`, `mobile`에 의존하지 않는다"를
개정해야 한다. 그 제약은 첫 슬라이스를 작게 유지하기 위한 조건이었고 비동기 전환으로 전제가
바뀌었다. 개정 시 `minsu`가 참조하는 `project` 공개 API 범위를 draft 반영 한 가지로 한정하고,
`signal`과 `mobile`에 대한 비의존은 그대로 유지한다.

`signal`이 조립을 맡는 안은 채택하지 않았다. ADR을 건드리지 않는 대신 생성 원장 소유가
`signal`로 넘어가 원장 소유 결정과 어긋나고, Signal action의 멱등·충돌 정책을 담당하는 모듈이 LLM 작업의
lease·재시도·백오프 운영까지 떠안게 된다.

`minsu`는 `notification`처럼 자기 watermark(consumer offset)를 소유한다. `OutboxEventReader`가
조회만 제공하고 소비 상태는 consumer가 관리하는 기존 규약을 그대로 따른다.

## 7. 상태 전이와 모바일 계약

### 7.1 상태 전이

원장 상태와 API가 노출하는 상태를 분리한다.

| 원장 상태 | 의미 | API 노출 |
| --- | --- | --- |
| `pending` | 소비 완료, 생성 대기 | `generating` |
| `processing` | lease 보유, 생성 중 | `generating` |
| `succeeded` | 모델 draft를 `tasks`에 반영 완료 | `ready` |
| `exhausted` | 재시도 상한 도달, 고정 fallback 확정 | `ready` |

**API는 `generating`과 `ready` 두 값만 노출한다.** `exhausted`를 별도 값으로 드러내지 않는
이유는 동기 설계 5절의 "fallback 여부와 이유는 public API 응답에 넣지 않고 내부 관측으로
남긴다"를 그대로 유지하기 위해서다. 앱에게 필요한 정보는 "이 title이 앞으로 더 바뀌는가"
하나이고, `exhausted`는 더 바뀌지 않는다는 점에서 `succeeded`와 같다. fallback 여부는 원장과
관측에만 남는다.

### 7.2 모바일 계약 변경

`ConvertToTaskResponse`에 draft 생성 상태를 추가한다. 필드 추가이므로 기존 클라이언트에
무해한 하위 호환 확장이다.

```text
{
  "task":   { "id", "title", "status", "draft_status": "generating" | "ready" },
  "signal": { "id", "action" }
}
```

- 비동기가 비활성이면 항상 `ready`로 응답한다. 동기 경로와 계약이 갈라지지 않는다.
- 앱은 `generating`일 때 로딩 표시를 할지, fallback title을 그대로 보여주고 조용히 갱신할지
  스스로 정한다. 서버는 사실만 알리고 UX 판단을 앱에 남긴다.
- task 상세 조회 응답에도 같은 값을 노출해 앱이 재조회로 종료를 확인할 수 있게 한다.
- push 통지는 1차 범위에서 제외한다. push는 토큰 무효·알림 권한 거부·앱 종료로 수신 보장이
  약해 재조회 경로를 대체하지 못하고 그 위에 얹는 최적화이므로, 재조회가 자리 잡은 뒤 별도로
  판단한다.

이 절은 **모바일과의 합의 대상**이며 현재는 서버 제안이다. 합의 전까지 구현 티켓을 열지
않는다.

## 8. 경합·멱등성·실패 복구

### 8.1 사용자 수정과 AI 결과의 경합

**사용자 수정이 항상 우선한다.** convert 직후 사용자가 `TaskEditor.update`로 제목을 고쳤는데
뒤늦게 도착한 AI 결과가 이를 덮으면 사용자가 명시적으로 한 편집이 사라진다.

판정은 **조건부 UPDATE(compare-and-set)** 로 한다. 반영 시점에 `tasks`의 title/role/priority가
여전히 convert 시점의 고정 fallback 값과 같을 때만 갱신한다. 값이 달라졌으면 사용자가 이미
편집한 것이므로 AI 결과를 폐기하고 원장을 닫는다.

`updated_at` 비교를 쓰지 않는 이유는 서버의 다른 갱신(예: 체크리스트 토글)에도 걸려 실제
편집이 없는데 결과를 버리는 오탐이 생기기 때문이다. 조건부 UPDATE는 오탐이 없고 갱신과 판정이
한 문장에서 원자적으로 끝난다.

### 8.2 멱등성

- outbox 소비는 at-least-once다. 같은 event를 두 번 읽어도 원장의 `task_id` UNIQUE가 두 번째
  materialize를 막는다.
- 생성 실행은 lease로 직렬화한다. lease를 잡은 인스턴스만 모델을 호출한다.
- 반영은 조건부 UPDATE라 두 번 적용해도 결과가 같다. 첫 번째가 값을 바꾸면 두 번째는 조건에
  걸려 아무것도 하지 않는다.

### 8.3 실패 복구

| 상황 | 복구 |
| --- | --- |
| 소비 후 materialize 전 프로세스 종료 | watermark가 전진하지 않아 다음 주기가 같은 구간을 다시 읽는다 |
| lease 보유 중 프로세스 종료 | lease 만료 후 다음 주기가 회수해 재시도한다 |
| 모델 호출 실패·timeout | 백오프 후 재시도, 상한 도달 시 `exhausted` |
| 반영 직전 종료 | 원장이 `processing`으로 남아 lease 만료 후 재시도, 조건부 UPDATE가 중복을 흡수한다 |

어떤 경로로 끝나든 `tasks`는 convert 시점부터 항상 유효한 draft를 갖는다. 비동기 생성이
전부 실패해도 사용자가 보는 것은 현재 동기 경로의 fallback 결과와 같다. **비동기 전환은
품질의 상한만 올리고 하한은 내리지 않는다.**

## 9. timeout·retry·관측

### 9.1 비동기 경로의 timeout

동기 경로의 8초(MOM-0806)는 **그대로 둔다**. 이 값은 원탭 action의 사용자 체감을 기준으로
정했고, 비동기 전환 후에도 동기 fallback 경로가 남는 동안 유효하다.

비동기 경로는 사용자가 기다리지 않으므로 별도 설정 키로 분리하고 더 큰 값을 갖는다. 구체적
값은 dev 재측정 뒤 확정한다. 동기 경로의 8초를 비동기 값으로 재사용하지 않는다. 두 경로가
같은 키를 공유하면 한쪽을 늘릴 때 다른 쪽의 사용자 체감이 함께 나빠진다.

재시도는 비동기 경로에서 **도입한다.** 동기 경로가 재시도를 두지 않은 이유는 사용자가 그
시간을 기다리기 때문이었고, 비동기에서는 그 제약이 없다. 백오프와 상한은 `push_deliveries`의
형태(고정 백오프 목록 + `MAX_ATTEMPTS`)를 따르되 값은 구현 티켓에서 정한다.

### 9.2 관측

동기 경로의 `momens.minsu.llm.generate` observation과 outcome/fallback reason tag를 그대로
쓰고, 비동기 경로임을 구분하는 low-cardinality tag를 추가한다. 여기에 원장 운영 지표를
더한다.

- 원장 상태별 건수(`pending`/`processing`/`succeeded`/`exhausted`)
- convert 커밋부터 반영까지의 end-to-end 지연
- 사용자 편집으로 결과가 폐기된 건수 (조건부 UPDATE 미적용)
- 재시도 횟수 분포와 `exhausted` 비율
- consumer watermark 지연

`exhausted` 비율과 편집 폐기 건수는 제품 판단에 직접 쓰인다. 전자가 높으면 모델·프롬프트
문제이고, 후자가 높으면 비동기 생성이 사용자보다 느려 가치가 없다는 신호다.

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

1. **동기 유지 + 원장 도입** — 비동기 생성을 기본 비활성으로 두고 원장·소비자·관측만 배포한다.
   동작은 현재와 같다.
2. **dev 활성화** — dev에서 비동기를 켜고 end-to-end 지연, `exhausted` 비율, 편집 폐기 건수를
   측정한다. 3구간 timeout 예산의 미측정 구간(4절)도 이때 함께 채운다.
3. **모바일 계약 확장** — 합의된 `draft_status`를 응답에 추가한다. 비활성이면 항상 `ready`라
   앱은 단계 1·2에서도 안전하게 배포할 수 있다.
4. **prod 활성화** — 데이터 레지던시 정책(동기 설계 11절)과 dev 재측정이 끝난 뒤 판단한다.

각 단계는 앞 단계로 되돌릴 수 있다. 설정을 끄면 convert는 동기 경로로 돌아가고 `tasks`는
여전히 유효한 draft를 갖는다.

## 12. 후속 구현 티켓

이 문서는 설계만 확정한다. 구현은 다음으로 분리한다.

1. **원장·소비자 기반** — `minsu` 생성 원장 테이블, outbox consumer와 watermark, lease·재시도,
   조건부 UPDATE 반영, 관측. 기본 비활성.
2. **ADR-0014 개정** — `minsu → project` 단방향 의존 허용과 참조 범위 한정.
3. **모바일 계약 확장** — `draft_status` 응답 필드. 모바일 합의 후 착수.
4. **dev 측정** — end-to-end 지연, `exhausted` 비율, 편집 폐기 건수, 3구간 timeout 미측정 구간.
5. **prod 마이그레이션** — `momens-api`에 원장 테이블 마이그레이션 PR.

검증 기준은 각 티켓이 소유한다. 이 문서는 어떤 값이 근거를 갖춰야 하는지만 정한다.

## 13. 미확정으로 남는 것

- 비동기 경로의 timeout 값과 재시도 백오프·상한 — dev 재측정 후
- 원장 테이블의 세부 컬럼과 Flyway 파일명 — 구현 티켓
- `draft_status` 필드명과 노출 위치 — 모바일 합의
- prod 데이터 레지던시 정책 — 동기 설계 11절에서 이월된 미결
- `minsu → retrieval` 연동 — 별도 설계
