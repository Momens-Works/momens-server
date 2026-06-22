# 코드

코드 작성 규칙입니다 — 코딩 스타일 · Spring · DTO · 로깅 · 테스트.

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
  정해진 뒤 확정합니다([P1/P2](../pending-decisions.md)).
- API 성공 응답에는 전역 wrapper(`success`, `data`)를 두지 않습니다. 응답/에러 코드
  규격은 [서버 명세 > API 응답과 에러 코드](../spec/api-response-error-codes.md)를 따릅니다.

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

## 로깅

- 로거는 Lombok `@Slf4j`를 사용합니다.
- 로그 인자는 문자열 연결 대신 `{}` placeholder로 전달합니다
  (`log.info("user {} created", userId)`).
- 민감 정보(비밀번호·토큰·개인정보 등)는 로그에 남기지 않습니다([설정 · 시크릿 > 시크릿](configuration.md#시크릿) 참고).
- 로그 레벨:
  - `error`: 시스템 오류·예외
  - `warn`: 경고·복구 가능한 문제
  - `info`: 주요 비즈니스 흐름
  - `debug`: 개발 디버깅
- 로그 출력 포맷(plain vs 구조적/JSON)은 관측 스택과 함께 정합니다([P13](../pending-decisions.md)).

## 테스트

테스트 프레임워크는 JUnit 5를 사용합니다. 테스트 유형:

- 애플리케이션 컨텍스트 로드 테스트
- Spring Modulith 경계 테스트
- controller / web 테스트
- service 단위 테스트
- repository 통합 테스트 — PostgreSQL Testcontainers 사용
  ([데이터 > 영속성 > DB](persistence.md#db) 참고, H2 미사용)
