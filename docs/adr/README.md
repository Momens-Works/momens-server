# ADR (Architecture Decision Records)

결이 갈렸거나 맥락이 필요한 결정을 시점 기록으로 남깁니다. 한 번 Accepted 된 결정은
고치지 않고, 바뀌면 새 ADR로 supersede 합니다. 새 ADR은 [`0000-template.md`](0000-template.md)를
복사해 작성합니다.

| # | 제목 | 상태 |
| --- | --- | --- |
| [0001](0001-modular-monolith-rules.md) | 모듈러 모놀리스 아키텍처 룰 | Accepted |
| [0002](0002-gradle-multi-module-boundaries.md) | Gradle 멀티모듈을 물리 모듈 경계로 사용 | Accepted |
| [0003](0003-auth-session-transport-model.md) | 인증 세션·전송 모델: 모바일/웹 하이브리드 | Accepted |
| [0004](0004-token-issuance-verification-stack.md) | 토큰 발급·검증 스택: Resource Server + JOSE | Accepted |
| [0005](0005-refresh-token-storage-model.md) | Refresh token 저장 모델: 서버 저장형 + PostgreSQL 원장 | Accepted |
| [0006](0006-api-path-and-versioning-policy.md) | API path·버저닝 정책: `/api` 단일화 + 전 엔드포인트 버저닝 | Accepted |
| [0007](0007-signal-backing-and-module-boundary.md) | Signal backing과 모듈 경계: 신규 `signals` + `signal` 모듈 | Accepted |
| [0008](0008-outbox-worker-projection-boundary.md) | Projection 경계: api-server outbox 발행 + worker 소비 | Accepted (notification 부분은 [0009](0009-notification-consumer-ownership.md)로 개정) |
| [0009](0009-notification-consumer-ownership.md) | Notification 소유권: api-server가 `signal.created` outbox consumer | Accepted |
| [0010](0010-event-contract-conventions.md) | 이벤트 계약 규약: event_type 네이밍·하위호환성·버저닝 | Accepted |
| [0011](0011-signal-evidence-and-task-draft-contract.md) | Signal evidence와 task draft 생산·저장 계약 | Accepted |
| [0012](0012-brief-signal-digest-backing.md) | 브리프 시그널 요약 문단의 backing과 모듈 소유권 | Accepted |
| [0013](0013-project-progress-derivation.md) | 프로젝트 진행률을 태스크에서 계산 | Accepted |
| [0014](0014-minsu-task-draft-module-and-llm-boundary.md) | Minsu task draft 모듈과 LLM 경계 | Superseded by [0015](0015-minsu-async-task-draft-generation.md) |
| [0015](0015-minsu-async-task-draft-generation.md) | Minsu task draft 비동기 생성과 `minsu` 영속성 소유 | Accepted |
