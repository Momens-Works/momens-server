# 기반 규칙 (RULES)

프로젝트를 관통하는, 잘 바뀌지 않는 규칙을 모읍니다. 섹션 단위로 관리하고, 한 섹션이
정말 커지면 그때 별도 파일로 분리합니다.

## 원칙

- 확실하지 않은 규칙은 **추론하지 않고 합의로** 추가합니다. 미정 항목은
  [추후 결정 로그](DECISIONS-PENDING.md)에 둡니다.
- "왜 그렇게 정했나"의 근거는 [ADR](adr/)에 남기고, 여기에는 **상시 지시(무엇을 지킬지)**만
  둡니다.

> 규칙은 토픽별로 하나씩 채워집니다. 기존 [CONVENTIONS.md](CONVENTIONS.md)와
> [ai/ARCHITECTURE.md](ai/ARCHITECTURE.md)의 내용은 토픽 작업 시 이곳으로 이관 후
> 원본을 제거합니다.

## Git

Git 워크플로는 GitFlow를 따르고, 커밋·브랜치·PR 형식은 아래로 통일합니다.

### 브랜치 전략

- `develop`: 기본 개발 브랜치
- `main`: 릴리즈·배포 브랜치
- 일반 PR 대상은 `develop`, 릴리즈 시 `develop` → `main`

### 브랜치 이름

`<이슈번호>-<타입>/<작업-내용>`

- 예: `15-feat/create-category`, `23-fix/category-not-found`
- 브랜치 타입: `feat`, `fix`, `docs`, `refactor`, `chore`

### 커밋 메시지

형식: `<type> (<domain>): <메시지>`

- 예: `feat (Category): 카테고리 생성 API 구현`
- `(<domain>)`은 도메인이 있을 때 사용하고, 없으면 생략합니다(예: `docs: 문서 수정`).

| Type | 의미 |
| --- | --- |
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서 |
| `style` | 포맷·비동작 스타일 |
| `refactor` | 동작 보존 리팩터링 |
| `test` | 테스트 코드 |
| `chore` | 빌드·의존성·잡무 |
| `rename` | 파일/폴더 이름 변경·이동 전용 |
| `remove` | 파일 삭제 전용 |
| `!HOTFIX` | 긴급 critical 버그 수정 |

### 이슈 / PR 제목

`[Feature] / [Bug] / [Refactor] / [Chore] / [Docs] <제목>`

- 예: `[Feature] 카테고리 생성 API 구현`

### 리뷰

- PR은 최소 1명에게 리뷰 요청(권장). 승인은 강제하지 않습니다.
- `LGTM`만 남기지 않고, 확인한 범위·판단 근거를 짧게 남깁니다.

### 머지

- **rebase merge로 통일** — 머지 커밋 없이 선형 히스토리를 유지합니다.
- `develop`/`main`은 PR로만 변경(직접 push 차단).
- CI(`build`, `pr-format`) 통과 + 리뷰 대화 resolve 후 머지.
- 머지된 브랜치는 자동 삭제. force-push·브랜치 삭제 차단.
- 위 머지 규칙은 GitHub ruleset(`protected-branches`)으로 강제됩니다.

## 코딩 스타일

포맷은 도구로 강제하고, 도구가 못 잡는 명명·관용은 아래 규칙을 따릅니다.

### 포맷

- **Spotless + Google Java Format** (2-space, 100칸)으로 강제합니다.
- 커밋 전 `./gradlew spotlessApply`로 정렬하고, CI가 `./gradlew spotlessCheck`로 검사합니다.
- 에디터 설정은 `.editorconfig`를 따릅니다.
- GJF가 자동 처리하므로 따로 신경 쓰지 않아도 되는 것: 와일드카드 import 금지, 모든
  제어문 중괄호, 연산자 공백, 배열 Java식(`String[]`).

### 네이밍

- 클래스: `XxxController` / `XxxService` / `XxxRepository`, 엔티티 `User`,
  DTO `XxxRequest` / `XxxResponse`.
- 메서드: 조회 `getX`·`findXById`·`findXList`, 생성 `createX`·`saveX`,
  수정 `updateX`·`modifyX`, 삭제 `deleteX`·`removeX`, 검증 `validateX`·`checkXExists`.
- 변수: camelCase. boolean 은 `isActive`·`hasPermission`. 컬렉션은 복수형(`users`).
- 상수: `UPPER_SNAKE_CASE`.

### Lombok

- 허용: `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`,
  `@Builder`, `@Slf4j`.
- 지양: `@Data`, 무분별한 `@Setter`.

### 기타

- `switch` 에는 `default` 를 둡니다.
- 불리언은 직접 평가합니다: `if (isActive)` (O), `if (isActive == true)` (X).

## Spring

### 의존성 주입

- 생성자 주입만 사용합니다(필드 주입 금지). `@RequiredArgsConstructor` + `private final`을 권장합니다.

### 레이어 책임

- Controller: HTTP 입출력에 집중하고 얇게 유지합니다.
- Service: 오케스트레이션·교차 엔티티 규칙·트랜잭션 경계.
- Entity: 자기 상태·불변식 로직(자신을 어떻게 바꿀지)을 담습니다.
- Repository: DB 접근을 캡슐화합니다(그 뒤로 숨김). 초기 접근은 JPA.
- 공통 인프라 코드는 platform 성격의 위치에 둡니다.

### 검증

- 요청 DTO에는 validation annotation을 사용합니다.

### 트랜잭션

- 트랜잭션 경계는 Service 메서드에 둡니다.

### 보안

- Spring Security 의존성은 초기부터 포함하되, 보안 설정 클래스는 인증/인가 구현 시점에 만듭니다.

## DTO

- request/response DTO는 Java record를 기본으로 합니다. class는 다음일 때만 사용합니다:
  기본 생성자 필요 / Jackson·JPA·외부 라이브러리 호환 / 기본값·복잡한 생성 로직.
- API DTO와 persistence 모델(엔티티)은 분리합니다.
- request/response DTO는 분리해서 둡니다. 정확한 패키지 경로는 모듈/패키지 구조가
  정해진 뒤 확정합니다([P1/P2](DECISIONS-PENDING.md)).

### 네이밍

- 요청: `{기능}Request` (예: `CreatePostRequest`)
- 응답: `{기능}Response` (예: `PostResponse`)

### 요청 DTO

- `@Schema`로 문서화합니다.
- 검증 annotation을 사용합니다(`@NotBlank`, `@NotNull`, `@Size` 등).
- 컬렉션 원소도 검증합니다: `List<@NotBlank String>`.

### 응답 DTO

- record + `@Schema`. 단순 생성은 생성자를, 정적 팩토리 메서드는 생성 의도를 드러내거나
  로직을 캡슐화할 때만 사용합니다.

## 영속성 (DB · JPA · Flyway)

### DB

- PostgreSQL만 지원합니다. 초기 DB 접근은 JPA. QueryDSL은 필요 시 추가합니다([P8](DECISIONS-PENDING.md)).
- DB 통합 테스트는 PostgreSQL Testcontainers를 사용합니다(H2 미사용).

### 엔티티

- `@Getter` 허용, `@Setter` 지양, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 필수,
  `@Builder` 허용.
- 생성자·정적 팩토리·`@Builder`는 생성 의도와 필드 수에 따라 선택합니다.
- 자기 상태·불변식 로직은 엔티티에 둡니다([Spring > 레이어 책임](#레이어-책임)).
- `equals`/`hashCode`에서 연관 필드는 제외합니다.

### 마이그레이션 (Flyway)

- Flyway로 관리하고, 위치는 `classpath:db/migration`입니다.
- 이미 적용된 마이그레이션은 수정하지 않습니다(변경은 새 마이그레이션으로).
- 파일명은 순차 버전 + snake_case 설명: `V<n>__<설명>.sql`
  (예: `V1__create_user.sql`, `V2__add_user_index.sql`).

## 테스트

테스트 프레임워크는 JUnit 5를 사용합니다. 테스트 유형:

- 애플리케이션 컨텍스트 로드 테스트
- Spring Modulith 경계 테스트
- controller / web 테스트
- service 단위 테스트
- repository 통합 테스트 — PostgreSQL Testcontainers 사용([영속성 > DB](#db) 참고, H2 미사용)

## 시크릿

- 실제 secret은 커밋하지 않습니다. 커밋 가능한 설정 파일에는 환경변수 placeholder만 둡니다.
- 커밋 금지: `.env`, `.env.*`, `application-secret.yml`, `application-*-secret.yml`.
  커밋 가능: `application.yml`, `application-{local,test,prod}.yml`, `.env.example`.
- secret 주입: 로컬은 `.env`, CI는 GitHub Actions Secrets, 운영은 Kubernetes Secret 또는
  External Secrets.
- 개인 DM·개인 메모·private submodule·private repo를 secret 저장소로 쓰지 않습니다.
- 자세한 운영은 [로컬 개발](LOCAL_DEVELOPMENT.md)을 참고합니다.

## 설정 (Configuration)

### 바인딩

- 설정은 타입 안전한 `@ConfigurationProperties`로 그룹 바인딩합니다. `@Value`는 일회성
  단순 값에만 사용합니다.
- `@ConfigurationProperties` 클래스는 record로 작성하고 `@Validated`로 검증합니다.

### 네임스페이스

- 애플리케이션 설정은 `momens.*` 아래 둡니다 (예: `momens.auth.*`, `momens.cors.*`).
- 프레임워크·서드파티 설정(`spring.*`, `management.*` 등)은 표준 키를 사용합니다.

### 프로필

- 프로필: `local`(기본) / `test` / `prod`. 활성화는 `SPRING_PROFILES_ACTIVE` 또는
  `--spring.profiles.active`.
- `application.yml`(공통) + `application-<profile>.yml`(환경별). 민감값은 env placeholder.

### 환경변수

- env → 설정은 Spring relaxed binding을 사용합니다
  (`momens.auth.jwt-secret` ← `MOMENS_AUTH_JWT_SECRET`).
- secret은 설정 파일에 두지 않고 env로 주입합니다([시크릿](#시크릿) 참고).

### 위치

- `@ConfigurationProperties` 클래스 위치는 모듈/패키지 구조([P1/P2](DECISIONS-PENDING.md))
  확정 후 정합니다. 지금은 `momens.*` 네임스페이스 원칙만 둡니다.
