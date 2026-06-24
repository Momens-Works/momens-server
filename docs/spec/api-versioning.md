# API 버저닝

이 문서는 `momens-server` HTTP JSON API의 버저닝 원칙을 정의합니다.
`momens-retrieval`의 gRPC 계약, `momens-worker`의 내부 API, 외부 webhook payload는 이 문서의
범위가 아닙니다.

## 원칙

- API 버저닝은 Spring Boot 4 / Spring Framework 7의 API versioning 기능을 사용합니다.
- 버전별 핸들러 분기는 Spring MVC request mapping의 `version` 속성으로 표현합니다.
- 클라이언트는 request header `API-Version`으로 요청 API 버전을 전달합니다.
- 공식 API path는 `/api` prefix를 사용합니다.
- 기존 레거시 호환 API는 당장 버저닝을 강제하지 않고, 기존 path도 호환용 alias로 유지할 수
  있습니다.

## 적용 대상

버저닝은 신규 API와 개편 API에 적용합니다.

- 새로 추가하는 공개 API
- 기존 API의 path, request, response, status, 에러 code를 호환되지 않게 바꾸는 개편 API
- Legacy compatible 응답보다 Standard 응답 모드를 우선하는 새 계약 API

기존 Go API를 이관해 레거시 호환성을 유지하는 엔드포인트는 기존 path와 호출 방식을 당장 제거하지
않습니다. 공식 path는 `/api` prefix를 사용하되, 기존 path는 같은 handler의 alias로 함께 제공할 수
있습니다. 예를 들어 `GET /api/me`, `PATCH /api/me`를 공식 path로 문서화하고, `GET /me`, `PATCH /me`는
legacy compatible alias로 유지할 수 있습니다.

API 명세서를 전달할 때는 `/api` prefix 도입 사실과 기존 path의 호환 정책을 함께 안내합니다.

## 버전 전달 방식

버전은 `API-Version` request header로 전달합니다.

```http
GET /api/posts/1 HTTP/1.1
API-Version: 1
```

URL에는 리소스 path를 유지하고, 버전 선택은 Spring API versioning resolver가 담당합니다.

## Controller 작성 방식

버전별 핸들러는 request mapping의 `version` 속성으로 구분합니다.

```java
@RestController
@RequestMapping("/api/posts")
public class PostController {

  @GetMapping(path = "/{id}", version = "1")
  public PostV1Response getPostV1(@PathVariable Long id) {
    return new PostV1Response(id, "title");
  }

  @GetMapping(path = "/{id}", version = "2")
  public PostV2Response getPostV2(@PathVariable Long id) {
    return new PostV2Response(id, "title", "author");
  }
}
```

컨트롤러 path는 공식 API path 기준으로 `/api` prefix를 사용합니다. 기존 레거시 path를 함께 제공해야
하면 같은 handler에 alias path로 매핑하고, Swagger/OpenAPI에는 `/api` prefix path를 공식 path로
문서화합니다.

## Breaking change 기준

다음은 breaking change입니다.

- path, HTTP method, 필수 query/path parameter 변경
- request body 필드 삭제, 이름 변경, 타입 변경, 필수 필드 추가
- response body 필드 삭제, 이름 변경, 타입 변경
- 성공 HTTP status 변경
- 클라이언트 분기 대상인 에러 code 삭제 또는 의미 변경
- 기존 nullable 필드를 non-null로 바꾸거나, 기존 optional 필드를 required로 바꾸는 변경

다음은 일반적으로 breaking change로 보지 않습니다.

- response body에 optional 필드 추가
- 새 에러 code 추가
- OpenAPI 설명, 예시, summary 수정
- 서버 내부 구현, DB schema, Java package 변경

## 구현 체크리스트

- 기존 Go API 이관인지, 신규/개편 API인지 먼저 결정합니다.
- 공식 API path에는 `/api` prefix를 사용합니다.
- 레거시 호환 API이면 기존 path와 호출 방식을 alias로 유지할 수 있습니다.
- 신규/개편 API이면 Spring API versioning의 `version` mapping을 사용합니다.
- 신규/개편 API 클라이언트는 `API-Version` header를 전달해야 합니다.
- API 명세서를 전달할 때 `/api` prefix 도입 사실과 기존 path 호환 정책을 함께 안내합니다.
