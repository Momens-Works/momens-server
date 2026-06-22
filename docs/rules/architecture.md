# 아키텍처

`momens-server`는 **modular modulith**입니다. 하나의 배포 가능한 Spring Boot 애플리케이션을
유지하면서 내부 모듈 경계를 명시적으로 둡니다.

- 하나의 배포 가능한 애플리케이션을 유지합니다(초기 단계에 마이크로서비스로 분리하지 않음).
- 프로젝트 구조는 Gradle 멀티모듈을 사용합니다. 실행 모듈은 `app`입니다.
- 모듈 경계는 Spring Modulith 테스트로 검증합니다.
- 공통 인프라 코드는 platform 성격의 위치에 둡니다.
- 레이어 책임(controller 얇게·service 트랜잭션·repository 캡슐화)은
  [Spring](code-conventions.md#spring)을 따릅니다.

## 패키지

- base package는 `works.momens.server`입니다.
- 도메인 모듈 목록과 모듈 간 의존 방향은 아직 미정입니다([P1/P2](../pending-decisions.md)).

시스템 맥락(레포 역할)은 [AGENTS.md](../../AGENTS.md)와 [온보딩](../onboarding.md)을
참고합니다.
