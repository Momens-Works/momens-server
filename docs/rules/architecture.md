# 아키텍처

`momens-server`는 **modular modulith**입니다. 하나의 배포 가능한 Spring Boot 애플리케이션을
유지하면서 내부 모듈 경계를 명시적으로 둡니다.

- 하나의 배포 가능한 애플리케이션을 유지합니다(초기 단계에 마이크로서비스로 분리하지 않음).
- 프로젝트 구조는 Gradle 멀티모듈을 사용합니다. 실행 모듈은 `app`입니다.
- 모듈 경계는 Spring Modulith 테스트로 검증합니다.

결정 근거는 [ADR-0001](../adr/0001-modular-monolith-rules.md)에 있습니다.

## 모듈 경계

- 모듈은 기본적으로 비즈니스 capability 기준으로 나눕니다.
- 기술 계층(controller, service, repository 등)은 모듈 경계가 될 수 없습니다.
- bounded context는 모델과 언어의 의미가 달라질 때 보조 기준으로 사용합니다.
- workflow는 1차 모듈 경계로 삼지 않습니다. 필요하면 각 capability의 public API만 조합하는
  얇은 orchestration module로 두며, orchestration module은 도메인 정책을 소유하지 않습니다.

## 모듈 내부 구조

- 기본 구조는 presentation · application · domain · infrastructure 레이어로 나눕니다.
  - presentation: HTTP 요청/응답, Controller, Request/Response DTO, 예외 응답 매핑
  - application: use case orchestration, 트랜잭션 경계, 모듈 간 협력
  - domain: 핵심 비즈니스 규칙, entity, value object, domain service
  - infrastructure: JPA, Redis, 외부 API client, messaging adapter 등 기술 의존 구현
- 작은 모듈은 `internal` 아래 단순 package-private 구조를 허용합니다.
- 다른 모듈·외부 시스템 의존이 application/domain을 오염시키면 port/adapter를 도입합니다.
- 모든 모듈에 hexagonal/clean architecture를 일괄 강제하지 않습니다.
- 레이어 책임(controller 얇게·service 트랜잭션·repository 캡슐화)은
  [Spring](code-conventions.md#spring)을 따릅니다.

## 모듈 간 의존

- 기본은 application event 기반 협력입니다.
- external 연동(minsu, slack 등)은 port/adapter 적용을 고려합니다.
- 단순한 경우 상대 모듈의 public API 직접 참조를 허용하되, 리뷰 단계에서 세부 논의합니다.
- 다른 모듈의 `internal` package 참조와 순환 의존은 금지합니다.
- event listener는 얇게 유지하고 application service를 호출합니다.

## Spring Modulith 적용

- `ApplicationModules.of(Application.class).verify()` 테스트를 두고 CI에 포함합니다.
- 모듈 root package를 public API 영역으로 보고, 다른 모듈의 `internal` package 참조를 금지합니다.
- root package 외 하위 package를 외부 모듈에 공개하지 않습니다. 공개가 필요하면
  `NamedInterface` 도입을 검토합니다.
- event publication registry는 적용합니다.
- `allowedDependencies`, `@ApplicationModuleTest`, Documenter는 필요 시점에 점진 도입합니다.

## Architecture Spike

- 별도 PoC 대신 첫 기능 구현을 Architecture Spike로 사용해 위 룰을 검증합니다.

## 패키지

- base package는 `works.momens.server`입니다.
- 도메인 모듈 목록과 external/persistence 모듈 분리 여부는 아직 미정입니다([P2/P10](../pending-decisions.md)).

시스템 맥락(레포 역할)은 [AGENTS.md](../../AGENTS.md)와 [온보딩](../onboarding.md)을
참고합니다.
