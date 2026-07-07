# 0008. Projection 경계: api-server outbox 발행 + worker 소비

- 상태: Accepted
- 날짜: 2026-07-06
- 작성자: Kimgyuilli

> **2026-07-07 개정**: 아래 본문의 "worker가 projection/notification을 처리"에서 **notification 소유권은
> [ADR-0009](0009-notification-consumer-ownership.md)로 이관**됐다(api-server가 `signal.created`를 소비해
> 발송). projection 경계와 worker의 소비·재시도·DLQ 소유는 유효하다.

## 맥락

레거시 `momens-api`는 도메인 write와 같은 트랜잭션에서 retrieval projection row를 직접 쓴다. 신규
`momens-server`는 모바일 Signal action과 task 생성 흐름을 구현하면서 retrieval projection을 어떻게
반영할지 정해야 했다.

Signal 발생 push notification, Signal action 후속 처리, task projection은 api-server, worker, retrieval
경계를 모두 지난다. api-server가 retrieval schema를 직접 알게 되면 도메인 write와 read-model projection이
다시 결합된다.

## 결정

api-server는 retrieval projection을 직접 쓰지 않는다. 도메인 write 트랜잭션 안에서는 append-only
`outbox_events` row만 남긴다. worker는 outbox를 소비해 projection/notification을 처리하고, retrieval은
worker가 쓴 `retrieval_events`를 polling/indexing한다. outbox consumer의 재시도와 DLQ는 worker가 소유한다.

Outbox payload는 ID 중심의 작은 데이터로 둔다. consumer는 필요한 경우 공유 DB에서 최신 상태를 다시 읽어
hydrate한다. MVP 이벤트는 다음 4개다.

- `signal.created`
- `task.created`
- `signal.converted_to_task`
- `signal.dismissed`

이벤트를 발생시킨 producer가 같은 트랜잭션에서 outbox row를 남긴다. worker는 `signals` 생성과 함께
`signal.created`를 남기고, api-server는 사용자 action 및 task 생성과 함께 관련 이벤트를 남긴다.

## 대안

- api-server가 retrieval projection을 직접 write: end-to-end 반영은 단순하지만, retrieval schema와
  api-server 도메인 write가 강하게 결합된다. 레거시와 같은 인라인 projection 결합을 새 서버에 반복한다.
- application event만 사용: 모듈 내부 이벤트에는 적합하지만, worker/retrieval이 소비해야 하는 cross-process
  계약과 재처리/관측 책임을 표현하기에는 부족하다.
- payload에 전체 snapshot 포함: consumer hydrate 비용은 줄지만 이벤트 계약이 커지고 변경에 취약해진다.

## 결과

api-server의 도메인 write와 retrieval projection 책임이 분리된다. action 트랜잭션은 도메인 row와 outbox row의
원자성만 보장하고, projection/notification은 결과적 일관성으로 처리된다.

대신 worker는 outbox consumer, projection writer, 재시도/DLQ 운영을 구현해야 하며, MVP 완료 정의에는
worker 소비와 task projection까지 포함된다.
