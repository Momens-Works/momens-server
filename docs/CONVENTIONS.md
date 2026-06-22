# 컨벤션

`momens-server`의 초기 컨벤션입니다.
세부 규칙은 세팅과 구현을 진행하면서 필요한 만큼 추가합니다.

## 패키지

기본 패키지:

```text
works.momens.server
```

실제 Gradle 모듈 목록은 추후 결정합니다.
아래는 논의용 예시입니다.

```text
works.momens.server.platform
works.momens.server.workspace
works.momens.server.product
works.momens.server.memory
works.momens.server.source
works.momens.server.retrievalintegration
```

검색 연동 모듈은 별도 서비스 `momens-retrieval`과 구분하기 위해
`retrieval-integration`을 우선 후보로 둡니다.

## Spring 컨벤션

- 생성자 주입을 사용합니다.
- 필드 주입은 사용하지 않습니다.
- Controller는 HTTP 입출력에 집중합니다.
- 비즈니스 규칙은 Service에 둡니다.
- 초기 persistence 접근은 JPA를 사용합니다.
- DB 접근은 Repository 뒤에 둡니다.
- 공통 인프라 코드는 platform 성격의 모듈에 둡니다.
- 요청 DTO에는 validation annotation을 사용합니다.
- 트랜잭션 경계는 Service 메서드에 둡니다.
- Spring Security 의존성은 초기부터 포함하지만, 보안 설정 클래스는 인증/인가 구현 시점에 만듭니다.

## DTO 컨벤션

- request/response DTO에는 Java record 사용을 우선 검토합니다.
- API DTO와 persistence model은 분리합니다.
- 초기 세팅 단계에서 새 공통 응답 포맷을 정하지 않습니다.
- 마이그레이션 단계에서는 기존 Go API 응답 호환을 우선합니다.

## DB 컨벤션

- PostgreSQL만 지원합니다.
- 마이그레이션은 Flyway를 사용합니다.
- 초기 DB 접근은 JPA를 사용합니다.
- QueryDSL은 필요한 시점에 추가합니다.
- 이미 적용된 migration은 수정하지 않습니다.
- migration 이름은 순서와 의미가 드러나게 작성합니다.
- DB 통합 테스트는 PostgreSQL Testcontainers를 사용합니다.
- repository/migration 호환성 테스트에 H2를 사용하지 않습니다.

## 테스트 컨벤션

초기 테스트 유형:

- application context load test
- Spring Modulith boundary test
- controller/web tests
- service unit tests
- repository integration tests with PostgreSQL Testcontainers

## 포맷팅

Use Spotless with Google Java Format.

CI should run:

```bash
./gradlew spotlessCheck
./gradlew test
./gradlew bootJar
```

Developers can format Java code locally with:

```bash
./gradlew spotlessApply
```

## API Compatibility

초기 세팅 단계에서는 새 공통 API 응답 포맷을 정하지 않습니다.
우선 기존 Go API와의 호환을 원칙으로 두고, 응답 포맷 정리는 마이그레이션
단계에서 결정합니다.

## CODEOWNERS

전체 레포의 코드 오너는 백엔드 팀입니다.

```text
* @Momens-Works/momens-backend
```

## 시크릿

실제 secret은 커밋하지 않습니다.
로컬 credential은 `.env` 또는 무시되는 로컬 파일에서 관리합니다.

커밋 가능한 설정 파일에는 실제 값이 아니라 환경변수 placeholder만 둡니다.

커밋 가능:

```text
application.yml
application-local.yml
application-test.yml
application-prod.yml
.env.example
```

커밋 금지:

```text
.env
.env.*
application-secret.yml
application-*-secret.yml
```

로컬 secret 공유는 팀 secret manager를 사용합니다.
CI secret은 GitHub Actions Secrets에 저장합니다.
운영 secret은 Kubernetes Secret 또는 External Secrets로 관리합니다.

개인 DM, 개인 메모, private submodule, private repo를 secret 저장소로 사용하지
않습니다.
