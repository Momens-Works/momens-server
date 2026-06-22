# 로컬 개발

이 문서는 프로젝트 skeleton이 만들어지면서 함께 갱신합니다.

## 필요 도구

- JDK 21
- Git
- Docker
- 이 레포의 Gradle wrapper

전역 Gradle 대신 레포에 포함된 Gradle wrapper를 사용합니다.

## 예상 명령어

프로젝트 skeleton 생성 후:

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

## DB

PostgreSQL을 사용합니다.
로컬 개발용 `docker-compose.yml`은 `pgvector/pgvector` 이미지를 사용합니다.

테스트에서는 PostgreSQL Testcontainers를 사용합니다.
H2 호환성을 가정하지 않습니다.
