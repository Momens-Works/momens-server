# 0009. Notification 소유권: api-server가 `signal.created` outbox consumer

- 상태: Accepted
- 날짜: 2026-07-07
- 작성자: Kimgyuilli

## 맥락

[ADR-0008](0008-outbox-worker-projection-boundary.md)은 api-server가 도메인 write 트랜잭션에서
`outbox_events` row만 append하고, worker가 outbox를 소비해 **projection과 notification**을 처리한다고
정했다.

2026-07-07 worker/retrieval 계약 미팅에서 Signal 발생 push notification의 소비 주체를 다시 논의했다.
`signal.created`는 worker가 발행한다. 그 notification까지 worker가 다시 소비하면 "worker 발행 → worker
소비"로 outbox를 왕복하는 부자연스러운 흐름이 된다. Signal 발생 알림은 도메인 이벤트를 읽어 사용자에게
알리는 api-server 관심사에 가깝고, api-server는 이미 모바일 사용자 API·인증·푸시 대상 사용자 맥락을
소유한다.

## 결정

Signal 발생 push notification은 **api-server가 `signal.created` outbox 이벤트를 consumer로 소비**해
발송한다. worker는 `signal.created`를 발행(producer)만 하고 notification은 발송하지 않는다.

- 이 개정은 **notification 소유권에 한정**한다. projection 후속 처리는 ADR-0008대로 worker가 유지한다.
- api-server는 이제 outbox producer이자 (notification 목적의) consumer가 된다. api-server의 notification
  소비도 `idempotency_key` 기준 멱등, `outbox_events.id` watermark 기반 폴링 원칙을 따른다.
- MVP notification은 `signal.created` 1종이다. task 생성·dismiss·task 상태 변경 알림은 MVP 이후다.
- notification 소비/발송을 api-server 내부 어느 모듈이 소유하는지(기존 `signal` 모듈 vs 별도 notification
  관심사)는 이 ADR에서 정하지 않고 구현 PR에서 정한다.

## 대안

- **worker가 notification까지 소유(ADR-0008 원안)**: 소비 지점을 worker로 단일화하지만, worker가 자기
  발행 이벤트를 자기 소비하는 왕복이 생기고, 푸시 대상 사용자 맥락·모바일 세션을 소유하지 않은 worker에
  사용자 알림 책임이 붙는다.
- **api-server가 `signal.created` 발행까지 소유**: Signal 원본은 worker가 생성하므로 발행 주체는 worker가
  자연스럽다. 발행은 worker, 소비(notification)만 api-server로 나눈다.

## 결과

- api-server가 outbox consumer 역할을 갖는다. ADR-0008의 "worker가 … notification 처리" 부분을
  개정하며, projection 경계(api-server 발행 → worker 소비·projection, 재시도/DLQ worker 소유)는 유효하다.
- ADR-0008의 outbox 소비 상태·재시도·DLQ worker 소유 규정은 **projection 경로에 한정**되고, api-server의
  notification 소비는 동일한 멱등·watermark 원칙을 따르되 소비 상태는 api-server가 관리한다.
- notification 발송 모듈 위치는 구현 PR의 후속 결정으로 남는다.
