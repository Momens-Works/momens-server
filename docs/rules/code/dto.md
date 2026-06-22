# DTO

- request/response DTO는 Java record를 기본으로 합니다. class는 다음일 때만 사용합니다:
  기본 생성자 필요 / Jackson·JPA·외부 라이브러리 호환 / 기본값·복잡한 생성 로직.
- API DTO와 persistence 모델(엔티티)은 분리합니다.
- request/response DTO는 분리해서 둡니다. 정확한 패키지 경로는 모듈/패키지 구조가
  정해진 뒤 확정합니다([P1/P2](../../DECISIONS-PENDING.md)).

## 네이밍

- 요청: `{기능}Request` (예: `CreatePostRequest`)
- 응답: `{기능}Response` (예: `PostResponse`)

## 요청 DTO

- `@Schema`로 문서화합니다.
- 검증 annotation을 사용합니다(`@NotBlank`, `@NotNull`, `@Size` 등).
- 컬렉션 원소도 검증합니다: `List<@NotBlank String>`.

## 응답 DTO

- record + `@Schema`. 단순 생성은 생성자를, 정적 팩토리 메서드는 생성 의도를 드러내거나
  로직을 캡슐화할 때만 사용합니다.
