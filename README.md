# momens-server

Momens의 새로운 Java Spring 제품 API 서버입니다.

이 레포는 기존 Go/Gin 기반 `momens-api`를 대체하는 새 Java Spring 제품 API 서버입니다.

## 아키텍처 방향

`momens-server`는 **modular modulith**를 지향합니다.

- 런타임은 하나의 Spring Boot 애플리케이션입니다.
- 물리 모듈 경계는 Gradle 서브프로젝트(기능/도메인 단위)이며, `app`이 조립·실행합니다.
- Spring Modulith를 병행해 모듈 간 의존·경계를 검증/문서화합니다.
- 마이크로서비스로 분리하지 않습니다.
- 도메인 모듈 목록은 기능이 추가되며 점진적으로 늘립니다([ADR-0002](docs/adr/0002-gradle-multi-module-boundaries.md)).

## 기본 스택

| Area | Choice |
| --- | --- |
| JDK | 21 |
| Framework | Spring Boot 4 |
| Build | Gradle |
| Gradle DSL | Groovy |
| Architecture support | Spring Modulith |
| Database | PostgreSQL |
| Migration | Flyway |
| Persistence | JPA |
| Formatting | Spotless with Google Java Format |
| Test | JUnit 5, Testcontainers |

## 문서

- [문서 인덱스](docs/README.md) — 전체 문서 네비게이션
- [온보딩](docs/onboarding.md) — 처음 합류했다면 여기서 시작
- [로컬 개발](docs/local-development.md)
- [기반 규칙](docs/rules/README.md)
- [ADR](docs/adr/)
- [Agent Guide](AGENTS.md) — AI 진입점(Codex/Claude 공용), `CLAUDE.md`가 가리킴

사람이 읽는 `docs/` 문서는 한국어로 작성합니다. AI/에이전트 전용 진입점은
영어로 작성한 [AGENTS.md](AGENTS.md)이며, 별도 AI 문서 트리는 두지 않습니다.
