# 초기 세팅

최종 수정일: 2026-06-22

이 문서는 `momens-server`의 초기 세팅 목표를 정의합니다.
도메인 마이그레이션 계획서가 아닙니다.

목표는 두 명의 서버 개발자가 안정적으로 작업을 시작할 수 있는 Spring 기반을
만드는 것입니다.

## 맥락

Momens는 현재 여러 레포로 나뉘어 있습니다.

| Repository | Current role |
| --- | --- |
| `teams` | PRD, ADR, glossary, product language |
| `momens-api` | Legacy Go/Gin user-facing product API |
| `momens-worker` | Go ingestion and curation worker |
| `momens-retrieval` | Java Spring retrieval read-model server |
| `k8s` | Kubernetes and deployment infrastructure |

`momens-server`는 새로운 Java Spring API 서버입니다.
장기적으로 `momens-api`를 대체하지만, 첫 번째 마일스톤은 프로젝트 초기화입니다.

## 아키텍처 목표

목표 아키텍처는 **modular modulith**입니다.

의미:

- 하나의 Spring Boot 애플리케이션 artifact
- 하나의 배포 가능한 API 서버
- Gradle 멀티모듈 프로젝트 구조
- Spring Modulith를 통한 모듈 경계 검증
- 초기 단계에서 마이크로서비스로 분리하지 않음
- 실행 애플리케이션 모듈 이름은 `app`
- 나머지 모듈 이름은 추후 결정

아래는 확정된 모듈 목록이 아니라 논의를 위한 예시입니다.

```text
works.momens.server
├── platform
├── workspace
├── product
├── memory
├── source
└── retrieval
```

예상 책임:

| Package | Initial responsibility |
| --- | --- |
| `platform` | shared config, web errors, common response, database, security infrastructure |
| `workspace` | auth, workspace, member, RBAC |
| `product` | project, milestone, task, decision, blocker |
| `memory` | memory candidate and confirmed memory lifecycle |
| `source` | source connection setup |
| `retrieval-integration` | retrieval projection, retrieval client, Minsu boundary |

검색 연동 모듈을 만든다면 이름은 `retrieval-integration`을 우선 후보로 둡니다.
이 모듈은 별도 서비스인 `momens-retrieval` 자체가 아니라 API 서버 내부 연동
코드를 의미합니다.

## 기본 스택

| Area | Choice |
| --- | --- |
| Language | Java |
| JDK | 21 |
| Framework | Spring Boot 4 |
| Build tool | Gradle |
| Gradle DSL | Groovy |
| Architecture support | Spring Modulith |
| Database | PostgreSQL |
| Migration | Flyway |
| Persistence | JPA |
| Formatting | Spotless with Google Java Format |
| Test framework | JUnit 5 |

첫 커밋부터 Spring Boot 4와 JDK 21을 사용합니다.

## 초기 레이아웃

초기 레포 구조:

```text
momens-server
├── build.gradle
├── settings.gradle
├── gradle/
├── gradlew
├── gradlew.bat
├── README.md
├── docs/
│   ├── ARCHITECTURE.md
│   ├── CONVENTIONS.md
│   ├── INITIAL_SETUP.md
│   └── LOCAL_DEVELOPMENT.md
├── app/
├── {domain-module}/
├── .env.example
├── docker-compose.yml
├── AGENTS.md
└── .github/
    ├── CODEOWNERS
    ├── ISSUE_TEMPLATE/
    │   └── issue.md
    ├── PULL_REQUEST_TEMPLATE.md
    └── workflows/
        └── ci.yml
```

## 초기 의존성

초기 세팅과 개발 기반에 필요한 의존성만 추가합니다.

Core:

- Spring Boot Starter Web
- Spring Boot Starter Validation
- Spring Boot Starter Actuator
- Spring Boot Starter Security
- Spring Boot Starter Data JPA
- PostgreSQL Driver
- Flyway Core
- Flyway PostgreSQL support
- Spring Modulith Starter Core
- Springdoc OpenAPI
- spring-dotenv
- Lombok
- Spotless with Google Java Format

Testing:

- Spring Boot Starter Test
- Spring Security Test
- Spring Modulith Starter Test
- Testcontainers JUnit Jupiter
- Testcontainers PostgreSQL

Optional for first setup:

- Spring Boot Configuration Processor

초기에는 추가하지 않습니다:

- OAuth2 Client: Google OAuth 구현 시점에 추가
- QueryDSL: 쿼리 복잡도가 필요해질 때 추가

추론으로 의존성을 추가하지 않습니다. 필요한 의존성은 팀 논의 후 추가합니다.

## Suggested `build.gradle`

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.0'
    id 'com.diffplug.spotless' version '6.25.0'
}

group = 'works.momens'
version = '0.1.0-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
    runtimeOnly 'org.postgresql:postgresql'
    implementation 'me.paulschwarz:spring-dotenv:...'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:...'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testCompileOnly 'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'

    implementation 'org.springframework.modulith:spring-modulith-starter-core'
    testImplementation 'org.springframework.modulith:spring-modulith-starter-test'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
}

tasks.named('test') {
    useJUnitPlatform()
}

spotless {
    java {
        googleJavaFormat()
    }
}
```

정확한 Springdoc OpenAPI, Spring Modulith, spring-dotenv 좌표는 실제 프로젝트 생성
시점에 호환 버전을 확인합니다.

Gradle Wrapper 버전은 Spring Initializr가 생성하는 기본 버전을 따릅니다.

## CI

첫 세팅 PR에 `.github/workflows/ci.yml`을 추가합니다.

Minimum CI:

- checkout
- setup JDK 21
- setup Gradle cache
- run formatting check
- run tests
- build boot jar

CI는 pull request와 `main`, `develop` push에서 실행합니다.

```yaml
name: CI

on:
  pull_request:
  push:
    branches:
      - main
      - develop

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: gradle

      - uses: gradle/actions/setup-gradle@v4

      - name: Format
        run: ./gradlew spotlessCheck

      - name: Test
        run: ./gradlew test

      - name: Build
        run: ./gradlew bootJar
```

Docker publishing and Kubernetes deployment should wait until the application
has a stable health endpoint and at least one implemented vertical slice.

Dockerfile은 초기 세팅에 포함하지 않습니다.

## GitFlow

브랜치 전략은 GitFlow를 따릅니다.

- `develop`: 기본 개발 브랜치
- `main`: 릴리즈/배포 브랜치
- 일반 PR 대상은 `develop`
- 배포/릴리즈 시 `develop`에서 `main`으로 머지

초기 GitHub Actions workflow는 `ci.yml` 하나만 둡니다.

## CODEOWNERS

초기 `CODEOWNERS`는 전체 레포를 백엔드 팀 소유로 지정합니다.

```text
* @Momens-Works/momens-backend
```

## 설정 파일

초기 profile:

```text
application.yml
application-local.yml
application-test.yml
application-prod.yml
```

`application.yml`에는 최소 공통 설정만 둡니다.
환경별 값과 민감 정보는 `.env`, `.env.example`, profile 설정에서 다룹니다.

실제 `.env`는 커밋하지 않고, `.env.example`만 커밋합니다.

## 로컬 DB

로컬 개발용 `docker-compose.yml`을 둡니다.

PostgreSQL 이미지는 일반 `postgres`가 아니라 `pgvector/pgvector`를 사용합니다.
현재 Go API에 pgvector 마이그레이션이 있으므로 미리 호환성을 확보하기 위함입니다.

## 헬스체크

헬스체크는 Actuator 기본 `/actuator/health`만 사용합니다.
별도 `/health` 엔드포인트는 만들지 않습니다.

## GitHub 템플릿

초기 세팅에 아래 템플릿을 포함합니다.

```text
.github/ISSUE_TEMPLATE/issue.md
.github/PULL_REQUEST_TEMPLATE.md
```

Issue 템플릿은 하나만 두고, 제목 prefix로 `[Feature]`, `[Bug]`, `[Refactor]`,
`[Chore]`, `[Docs]`를 안내합니다.

## 첫 세팅 PR 체크리스트

- [ ] Spring Boot 4 project is generated.
- [ ] JDK 21 toolchain is configured.
- [ ] Gradle Groovy build is configured.
- [ ] Gradle multi-module structure is configured.
- [ ] Spring Modulith dependency is present.
- [ ] JPA dependency is present.
- [ ] Lombok is configured.
- [ ] Spotless with Google Java Format is configured.
- [ ] Springdoc OpenAPI is configured.
- [ ] spring-dotenv is configured.
- [ ] Basic application module verification test exists.
- [ ] Actuator health endpoint is available.
- [ ] Flyway is configured.
- [ ] Testcontainers PostgreSQL test exists.
- [ ] GitHub Actions CI runs `spotlessCheck`, `test`, and `bootJar`.
- [ ] CI runs on pull requests and pushes to `main` and `develop`.
- [ ] `.env.example` exists.
- [ ] `docker-compose.yml` uses `pgvector/pgvector`.
- [ ] PR and issue templates exist.
- [ ] `CODEOWNERS` points to `@Momens-Works/momens-backend`.
- [ ] `README.md` explains project purpose and stack.
- [ ] `docs/CONVENTIONS.md` exists.
- [ ] `docs/LOCAL_DEVELOPMENT.md` exists.
- [ ] No secrets are committed.

## 이번 단계에서 하지 않는 것

- migrating all Go API routes
- implementing full domain behavior
- matching every legacy API contract
- publishing Docker images
- editing Kubernetes manifests
- changing `momens-worker`
- changing `momens-retrieval`
- splitting into multiple services
- finalizing all Gradle module names before discussion
- adding a `LICENSE` file
