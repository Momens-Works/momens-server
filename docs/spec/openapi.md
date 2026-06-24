# OpenAPI

이 문서는 `momens-server` HTTP JSON API의 Swagger/OpenAPI 문서화 규칙을 정의합니다.

## 원칙

- 각 공개 엔드포인트는 OpenAPI에 성공 응답과 주요 실패 응답 예시를 함께 둡니다.
- 성공 응답에는 전역 wrapper(`success`, `data`)를 만들지 않습니다.
- 실패 응답 예시는 [API 응답과 에러 코드](api-response-error-codes.md)의 응답 모드를 따릅니다.
- Swagger/OpenAPI 문서화는 실제 public contract를 설명합니다. 내부 구현 세부사항을 노출하지 않습니다.
- 레거시 호환 alias path가 있으면 Swagger/OpenAPI에는 `/api` prefix가 붙은 공식 path를 기본으로
  문서화합니다.

## 구성 위치

OpenAPI 공통 설정과 커스터마이저는 실행·조립 모듈인 `app`에 둡니다.

```text
app/src/main/java/works/momens/server/support/openapi
```

이 위치에는 다음 구성을 둡니다.

- `OpenAPI` 기본 정보(title, version, description, server)
- Swagger UI가 사용하는 공통 component
- 실패 응답 예시를 보강하는 `OperationCustomizer`

기능 모듈에는 해당 기능의 controller, request/response DTO, controller docs interface를 둡니다.

## Controller 문서화

공개 API controller는 Swagger annotation을 구현 로직과 분리하기 위해 `XxxControllerDocs` 인터페이스를
둘 수 있습니다.

```java
@Tag(name = "User", description = "사용자 API")
interface UserControllerDocs {

  @Operation(summary = "내 사용자 정보 조회", description = "인증된 사용자 본인의 정보를 조회합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "내 사용자 정보 조회 성공",
      content = @Content(schema = @Schema(implementation = MeResponse.class)))
  @ApiExceptions({UserErrorCode.class, CommonErrorCode.class})
  MeResponse getMe(Principal principal);
}
```

실제 controller는 같은 signature로 docs interface를 구현합니다.

```java
@RestController
@RequiredArgsConstructor
class UserController implements UserControllerDocs {

  @Override
  @GetMapping({"/api/me", "/me"})
  public MeResponse getMe(Principal principal) {
    // ...
  }
}
```

`/me`처럼 기존 path를 함께 제공하는 경우에도 Swagger/OpenAPI의 공식 path는 `/api/me`입니다. 기존 path는
legacy compatible alias로 보고, 문서 노출 여부는 구현 시점에 별도로 조정합니다.

## 성공 응답

성공 응답은 실제 response DTO shape 그대로 문서화합니다.

- 단일 리소스 응답은 해당 response DTO를 `@ApiResponse`의 `schema`로 명시합니다.
- body가 없는 성공은 `204 No Content`로 명시합니다.
- 전역 성공 wrapper(`success`, `data`, `CommonApiResponse`)는 사용하지 않습니다.

```java
@ApiResponse(
    responseCode = "200",
    description = "내 사용자 정보 조회 성공",
    content = @Content(schema = @Schema(implementation = MeResponse.class)))
```

## 실패 응답

실패 응답 예시는 `@ApiExceptions`와 `OperationCustomizer`로 자동 생성합니다.

- `@ApiExceptions`에는 문서화할 `ErrorCode` enum class를 선언합니다.
- `OperationCustomizer`는 enum 상수를 읽어 HTTP status별 OpenAPI response와 example을 추가합니다.
- 공통 에러 enum 전체를 선언할 때는 해당 endpoint에 실제로 해당될 수 없는 에러까지 노출되지 않는지
  확인합니다.
- Standard 모드 예시는 아래 shape를 사용합니다.

```json
{
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "사용자를 찾을 수 없습니다."
  }
}
```

`details`가 필요한 에러 예시는 `details`를 포함할 수 있습니다. 민감 정보, 내부 예외 메시지, SQL,
stack trace는 예시에 넣지 않습니다.

Legacy compatible 모드 엔드포인트는 기존 Go API의 실패 body shape를 유지해 예시를 작성합니다.

```json
{
  "error": "unauthorized"
}
```

## DTO Schema

Request/response DTO는 `record`를 기본으로 하고 `@Schema`로 문서화합니다.

- record 자체에 설명을 둡니다.
- 주요 필드에는 `description`, `example`, `nullable`을 명시합니다.
- validation annotation과 `@Schema` 설명이 서로 어긋나지 않게 합니다.

## API Version Header

[API 버저닝](api-versioning.md)이 적용되는 신규/개편 API는 `API-Version` request header를 문서화합니다.

레거시 호환 API에는 전역으로 `API-Version` header를 강제 표시하지 않습니다. 버전 header는 실제로 Spring
API versioning이 적용된 operation에만 표시합니다.

## 구현 체크리스트

- controller docs interface를 둘지 먼저 결정합니다.
- 성공 응답은 실제 DTO shape 그대로 문서화합니다.
- 주요 실패 응답은 `@ApiExceptions`로 선언합니다.
- `@ApiExceptions`가 너무 넓은 공통 에러를 노출하지 않는지 확인합니다.
- 신규/개편 API는 `API-Version` header를 문서화합니다.
- 레거시 alias path가 있으면 `/api` prefix 공식 path와 호환 alias 정책을 구분합니다.
