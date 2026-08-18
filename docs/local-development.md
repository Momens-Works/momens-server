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

### 마이그레이션 checksum 불일치

Flyway checksum은 주석을 포함한 파일 내용 전체로 계산합니다. 이미 적용된 마이그레이션 파일이
바뀌면(MOM-0839의 `prod-schema` 헤더 추가처럼) 다음 기동에서 checksum 불일치로 실패합니다.
로컬 DB는 `docker-compose.yml`의 `momens-postgres-data` 볼륨에 남아 있으므로 컨테이너를 다시
띄우는 것만으로는 해소되지 않습니다.

```bash
docker compose down -v && docker compose up -d
```

테스트는 Testcontainers로 매번 새 DB를 쓰므로 영향을 받지 않습니다. `prod`는 Flyway가 꺼져 있어
무관하고, Flyway가 켜지는 `dev`는 같은 문제를 만나므로 DB를 다시 만들거나 `flyway repair`로
checksum을 갱신해야 합니다.

## 레거시 차등 비교

[이관 전략](design/legacy-product-api-migration-strategy.md)의 characterization은 같은 요청을 레거시
`momens-api`와 신규 서버에 적용해 결과를 비교하도록 정합니다. 그 비교를 실행하는 하네스가
`scripts/legacy-diff/`입니다(`MOM-0877`).

```bash
./scripts/legacy-diff/run.sh                    # 전체 실행 후 정리
KEEP=1 ./scripts/legacy-diff/run.sh             # 스택을 남겨 반복 실행
./scripts/legacy-diff/run.sh --only H020-member # 한 케이스만
```

레거시 저장소 위치는 `MOMENS_API_DIR`로 지정합니다. 기본값은 `<repo>/../momens-api`입니다.
worktree에서 실행하면 기본값이 맞지 않으므로 명시해야 합니다.

동작은 다음과 같습니다.

1. `momens-api`의 자체 `Dockerfile`로 레거시 이미지를 빌드합니다(최초 1회, 이후 재사용).
2. PostgreSQL 두 개와 레거시 서버를 띄웁니다. DB를 나누는 이유는 두 서버가 `users`,
   `workspaces`, `workspace_members`의 DDL을 각자 소유해 한 DB에서 두 마이그레이션을 함께
   돌릴 수 없기 때문입니다.
3. 신규 서버를 `bootJar` 결과로 띄워 Flyway가 스키마를 만들게 합니다.
4. `fixture.sql`을 양쪽 DB에 같은 내용으로 적용합니다. id와 시각을 고정하므로 응답 값이 문자
   그대로 같아야 합니다.
5. `cases.tsv`의 각 행을 두 서버에 보내고 status와 body를 대조합니다.

한 토큰으로 양쪽을 호출할 수 있는 것은 [ADR-0017](adr/0017-transitional-legacy-session-token-acceptance.md)이
정한 `session_token` 수용과 두 서버의 동일한 HS256 서명 키 덕분입니다. 이 전제가 깨지면
하네스도 함께 깨집니다.

### 케이스 추가

슬라이스마다 `cases.tsv`에 행을 추가합니다. 열은 `id`, `as`(픽스처 사용자 키), `method`,
레거시 path, 신규 path입니다. 필요한 데이터가 없으면 `fixture.sql`에 고정 id로 추가합니다.

### 결과 읽는 법

차이가 났다는 사실 자체는 실패가 아닙니다. 계약 문서가 확정한 의도된 차이인지 대조하는 것이
목적입니다. 의도하지 않은 차이는 계약 문서나 구현 중 하나가 틀렸다는 뜻입니다.

`--normalize`는 UUID와 타임스탬프를 자리표시자로 바꿉니다. 픽스처가 값을 고정하는 로컬
모드에서는 쓰지 않고, `--legacy-base`/`--server-base`로 dev 실서버를 가리켜 shape만 비교할 때
씁니다.

### 신규 필수 설정이 추가된 경우

신규 서버 부팅에 필요한 환경변수는 `scripts/legacy-diff/harness.conf`에 더미 값으로 둡니다.
기본값 없는 설정이 새로 생기면 부팅이 그 설정을 지목하며 실패하므로, 이 파일에 한 줄을
추가하면 됩니다.
