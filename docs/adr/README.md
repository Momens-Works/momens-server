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
