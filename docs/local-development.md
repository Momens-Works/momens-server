# 로컬 개발

이 문서는 로컬에서 `momens-server`를 실행하고 검증하는 방법을 정리합니다.

## 필요 도구

- JDK 21
- Git
- Docker
- 이 레포의 Gradle wrapper

전역 Gradle 대신 레포에 포함된 Gradle wrapper를 사용합니다.

## 기본 명령어

```bash
./gradlew test
./gradlew bootJar
./gradlew bootRun --args='--spring.profiles.active=local'
```

## 로컬 설정

로컬 개발에는 `.env`와 `application-local.yml`을 사용합니다.
`.env`는 커밋하지 않고, 필요한 키는 `.env.example`에 기록합니다.

초기 profile:

```text
application.yml
application-local.yml
application-test.yml
application-prod.yml
```

예상 설정 그룹:

- `server.port`
- `spring.datasource`
- `spring.flyway`
- `management.endpoints.web.exposure`
- `momens.auth`
- `momens.cors`
- `momens.retrieval`

## 민감 정보 관리

설정 파일은 **구조를 담는 파일**과 **실제 값을 담는 파일**을 분리합니다.

커밋하는 파일:

```text
application.yml
application-local.yml
application-test.yml
application-prod.yml
.env.example
```

커밋하지 않는 파일:

```text
.env
.env.*
application-secret.yml
application-*-secret.yml
```

`application-*.yml`에는 실제 secret을 넣지 않습니다.
대신 환경변수 placeholder만 둡니다.

예:

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}

momens:
  auth:
    jwt-secret: ${JWT_SECRET}
```

실제 값은 로컬에서는 `.env`, CI에서는 GitHub Actions Secrets, 운영에서는
Kubernetes Secret 또는 이후 도입할 External Secrets를 통해 주입합니다.

개인 DM, 개인 메모, private Git submodule, 별도 private repo로 secret을
전달하거나 저장하지 않습니다.

`.env.example`에는 실제 운영 secret을 두지 않습니다. 로컬 실행을 위한 기본 예시값은
둘 수 있지만, 공유가 필요한 민감 값은 별도 secret 관리 채널을 사용합니다.

## DB

PostgreSQL을 사용합니다.
로컬 개발용 `docker-compose.yml`은 `pgvector/pgvector` 이미지를 사용합니다.

테스트에서는 PostgreSQL Testcontainers를 사용합니다.
H2 호환성을 가정하지 않습니다.
