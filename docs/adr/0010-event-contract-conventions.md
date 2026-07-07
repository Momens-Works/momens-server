# 0010. 이벤트 계약 규약: event_type 네이밍·하위호환성·버저닝

- 상태: Accepted
- 날짜: 2026-07-08
- 작성자: Kimgyuilli

## 맥락

[ADR-0008](0008-outbox-worker-projection-boundary.md)이 outbox 발행/소비 경계와 payload를 **ID 중심의
작은 데이터**(consumer가 공유 DB에서 hydrate)로 정했다. 이벤트 종류가 늘면서, 세 가지를 팀 공용 규약으로
정할 필요가 생겼다: (1) `event_type` 이름을 어떻게 짓는가, (2) payload 스키마가 바뀔 때 어떻게 안 깨지게
하는가, (3) 정말 깨져야 할 때 버전을 어떻게 나누는가. 2026-07-07 미팅에서 이 셋을 "레퍼런스를 찾아보고
구체화"하기로 했다.

전제: 우리는 카프카가 아니라 **Postgres `outbox_events` 폴링**이다. `event_type`은 텍스트 컬럼 값이고
소비자는 그 값으로 라우팅한다. 따라서 카프카 토픽 네이밍 규칙은 **참고만** 하고, 토픽에 해당하는 규칙은
`event_type` 값에 적용한다.

## 결정

### 1. event_type 네이밍 = `{aggregate}.{과거형 동사}`

- **과거형**으로 쓴다(이벤트는 이미 일어난 사실). 명령형 금지: `signal.dismiss`(X) → `signal.dismissed`(O).
- **구체적으로** 쓴다: `signal.updated`처럼 두루뭉술하게 말고 무엇이 바뀌었는지 담는다.
- 세그먼트 구분은 `.`, 세그먼트 내부 다단어는 `_` (`signal.converted_to_task`).
- `aggregate_type`과 프리픽스가 중복되지만, `event_type` 단독으로 로그·라우팅에서 자기설명이 되도록
  프리픽스를 유지한다.
- 현행 이벤트가 이미 이를 따른다: `signal.created`, `task.created`, `signal.converted_to_task`,
  `signal.dismissed`.
- 발행 주체 컬럼은 `issued_by`다(인프라 용어 `producer` 회피 — 도메인이 메시징 스택을 알 필요 없음).

### 2. 하위호환성 = additive-only + tolerant reader

- payload는 **additive-only**로만 진화한다: 새 필드는 optional로만 추가하고, 기존 필드는 타입·의미
  변경·rename·재사용을 하지 않는다.
- 소비자는 **tolerant reader**다: 모르는 필드는 무시하고, 없는 필드는 견딘다.
- Schema Registry/Avro는 도입하지 않는다. payload는 `jsonb`이고, 호환성은 **도구가 아니라 규율과 코드로**
  강제한다. 이 두 규칙만으로 Backward 호환의 실질을 얻는다.

### 3. 버저닝 = 지금은 bare, breaking 시 event_type 접미사 `.vN`

- **지금**은 버전 세그먼트를 붙이지 않는다(bare = 암묵적 v1).
- **깨지는 변경**(필드 의미 변경·구조 재편 등 additive로 못 흡수하는 것)이 오면, `{aggregate}.{verb}.vN`
  **접미사 버전**으로 새 event_type을 만들고 전환기에 **dual-emit**한다. 접미사인 이유는 프리픽스가
  도메인이라 버전을 뒤에 둬야 라우팅·정렬에 덜 침습적이기 때문이다.
- envelope에 `schema_version` 컬럼은 두지 않는다.
- 여기서 정하는 건 **event_type(도메인 이벤트 의미) 축**이다. proto 패키지 버전(`momens.event.v1` 등,
  메시지 구조 축)과는 별개이며, 현재 outbox 이벤트는 proto 직렬화가 아니라 `jsonb`라 proto 버전과 직접
  얽히지 않는다.
- 버전을 지금 붙이지 않는 건 **비용이 아니라 편익 문제**다. 접미사 비용은 거의 0이지만, 지금은 소비자가
  전부 사내라 깨지는 변경이 와도 생산자·소비자를 같이 배포·재처리하면 되고, payload가 사실상 `{id}`
  수준(ADR-0008)이라 깨질 일 자체가 드물다. 버전이 값을 하는 건 '같이 배포하지 못하는 소비자'가 생길 때다.

## 대안

- **reverse-DNS 네이밍(CloudEvents `com.momens.signal.created`)**: 조직 간 이벤트 교환의 유일성·소유를
  위한 것. 사내 단일 시스템엔 프리픽스 비용만 늘어 불채택.
- **`schema_version` 컬럼 / payload 버전 필드**: 단일 event_type을 유지하지만 공통 필드가 늘고 소비자가
  버전 분기를 떠안는다. 첫 breaking change 때 event_type 접미사로 처리하면 컬럼을 안 늘려도 된다.
- **지금 Schema Registry 도입**: 스키마가 정말 자주·복잡하게 바뀌는 규모에서 값을 한다. 현재 규모엔 오버킬.

## 결과

- 이벤트 계약(이름·호환성·버전) 규약이 팀 공용으로 문서화된다. worker/api-server가 새 이벤트를 추가할 때
  이 규약을 따른다.
- payload=compact ID(ADR-0008)와 결합해, additive-only만 지키면 대부분의 payload 변경이 무중단으로 흡수된다.
- 첫 breaking change 시 `.vN` + dual-emit 절차가 이미 정의돼 있어 그때 바로 적용할 수 있다.

참고 리서치(국내외 사례·표준): [배민스토어 Zero-Payload](https://techblog.woowahan.com/13101/),
[Confluent 이벤트 설계](https://developer.confluent.io/courses/event-design/best-practices/) ·
[스키마 진화·호환성](https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html),
[CloudEvents `type` 규약](https://github.com/cloudevents/spec/blob/main/cloudevents/spec.md).
