# momens-server

Momens의 새로운 Java Spring 제품 API 서버입니다.

이 레포는 기존 Go/Gin 기반 `momens-api`를 대체하기 위한 새 서버 기반입니다.
현재 목표는 도메인 마이그레이션이 아니라 **프로젝트 초기 세팅**입니다.

지금 다루는 범위:

- Spring Boot 4 프로젝트 골격
- JDK 21 / Gradle Groovy 설정
- Gradle 멀티모듈 구조
- Spring Modulith 기반 modular modulith 방향
- 초기 의존성
- CI
- 로컬 개발 환경
- 팀 컨벤션

`momens-api`의 실제 API/도메인 마이그레이션은 초기 세팅이 안정된 뒤 별도로
계획합니다.

## 아키텍처 방향

`momens-server`는 **modular modulith**를 지향합니다.

- 런타임은 하나의 Spring Boot 애플리케이션입니다.
- 프로젝트 구조는 Gradle 멀티모듈을 사용합니다.
- 모듈 경계는 Spring Modulith로 검증합니다.
- 초기 단계에서 마이크로서비스로 분리하지 않습니다.
- 실행 애플리케이션 모듈 이름은 `app`입니다.
- 나머지 도메인 모듈 목록은 추후 확정합니다.

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
- [온보딩](docs/ONBOARDING.md) — 처음 합류했다면 여기서 시작
- [로컬 개발](docs/LOCAL_DEVELOPMENT.md)
- [기반 규칙](docs/rules/README.md)
- [ADR](docs/adr/)
- [추후 결정 로그](docs/DECISIONS-PENDING.md)
- [Agent Guide](AGENTS.md) — AI 진입점(Codex/Claude 공용), `CLAUDE.md`가 가리킴

사람이 읽는 문서는 한국어로 작성합니다. AI/에이전트가 읽는 구조 문서는 영어로
작성합니다.

## 현재 범위

포함:

- Spring Boot 4 skeleton
- Gradle Groovy 멀티모듈
- `app` 실행 모듈
- Flyway / JPA / PostgreSQL
- Spring Security 의존성
- Actuator 기본 `/actuator/health`
- Springdoc OpenAPI
- `.env` + `spring-dotenv`
- 로컬 `docker-compose.yml` PostgreSQL
- Spotless + Google Java Format
- GitHub Actions CI
- GitFlow (`develop` 기본 개발 브랜치, `main` 릴리즈 브랜치)
- CODEOWNERS

제외:

- 전체 API 마이그레이션
- 도메인 구현
- Dockerfile
- LICENSE
- Kubernetes 배포
- `momens-worker` 변경
- `momens-retrieval` 변경
