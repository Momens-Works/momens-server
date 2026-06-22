# 기반 규칙 (RULES)

프로젝트를 관통하는, 잘 바뀌지 않는 규칙을 성격별로 모읍니다.

## 원칙

- 확실하지 않은 규칙은 **추론하지 않고 합의로** 추가합니다. 미정 항목은
  [추후 결정 로그](../DECISIONS-PENDING.md)에 둡니다.
- "왜 그렇게 정했나"의 근거는 [ADR](../adr/)에 남기고, 여기에는 **상시 지시(무엇을 지킬지)**만
  둡니다.
- 한 토픽 파일이 너무 커지면 그때 더 잘게 나눕니다.

## 토픽

- [아키텍처](architecture.md) — modular modulith·모듈 경계·base package
- 협업
  - [Git](collaboration/git.md) — 브랜치·커밋·PR·머지
- 코드
  - [코딩 스타일](code/coding-style.md) — 포맷·네이밍·Lombok
  - [Spring](code/spring.md) — DI·레이어·트랜잭션·보안
  - [DTO](code/dto.md)
  - [로깅](code/logging.md)
  - [테스트](code/testing.md)
- 데이터
  - [영속성](data/persistence.md) — DB·JPA·Flyway
  - [시간 · 식별자](data/time-and-id.md)
- 설정
  - [설정 (Configuration)](config/configuration.md)
  - [시크릿](config/secrets.md)
