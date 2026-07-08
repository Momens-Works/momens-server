# API 응답과 에러 코드

이 문서는 `momens-server`의 HTTP JSON API 응답 규격을 정의합니다.
`momens-retrieval`의 gRPC 계약, `momens-worker`의 내부 처리 결과, 외부 webhook payload는
이 문서의 범위가 아닙니다.

## 원칙

- 기존 `momens-api`에서 이관하는 엔드포인트는 **HTTP status와 JSON body shape를 우선 보존**합니다.
- 성공 응답에는 전역 wrapper(`success`, `data`)를 두지 않습니다.
- Standard 모드에서 클라이언트는 HTTP status와 안정적인 error `code`로 분기합니다.
  `message` 문자열을 파싱하지 않습니다.
- 서버 내부 예외 메시지, SQL, stack trace, secret, 개인정보는 응답에 노출하지 않습니다.
- 각 엔드포인트는 OpenAPI에 성공/실패 예시를 함께 둡니다.

## 응답 모드

마이그레이션 기간에는 두 가지 응답 모드가 공존할 수 있습니다.

| 모드 | 사용 대상 | 실패 body |
| --- | --- | --- |
| Legacy compatible | 기존 Go API를 그대로 이관하는 엔드포인트 | `{ "error": "..." }` |
| Standard | 신규 엔드포인트 또는 계약 변경이 합의된 개편 엔드포인트 | `{ "error": { "code": "...", "message": "...", "details": { ... } } }` |

엔드포인트를 구현할 때는 어떤 모드인지 먼저 정하고, OpenAPI 예시에 명시합니다.
기존 엔드포인트의 모드를 Standard로 바꿀 때는 프론트엔드와 별도 합의가 필요합니다.

## 성공 응답

전역 wrapper는 사용하지 않습니다.

### 단일 리소스

레거시가 리소스를 직접 반환했다면 직접 반환합니다.

```json
{
  "id": "5d2f7f3a-5db1-4f2c-8b9e-13607dd1f5e8",
  "name": "Momens"
}
```

레거시가 이름 있는 wrapper를 사용했다면 같은 shape를 유지합니다.

```json
{
  "user": {
    "id": "5d2f7f3a-5db1-4f2c-8b9e-13607dd1f5e8",
    "email": "user@example.com"
  }
}
```

### 목록

목록은 컬렉션 이름으로 감싼 객체를 기본으로 합니다. 빈 목록은 `null`이 아니라 `[]`입니다.

```json
{
  "workspaces": []
}
```

### 명령 성공

반환할 리소스가 없는 성공 응답은 기존 Go API와 맞춰 `{ "message": "..." }`를 사용합니다.

```json
{
  "message": "updated"
}
```

새 엔드포인트에서 body가 정말 필요 없는 경우에만 `204 No Content`를 고려합니다. 기존 엔드포인트를
이관할 때는 레거시 응답을 우선합니다.

## 표준 에러 응답

Standard 모드의 에러 응답은 아래 형태를 사용합니다.

```json
{
  "error": {
    "code": "WORKSPACE_NOT_FOUND",
    "message": "워크스페이스를 찾을 수 없습니다.",
    "details": {
      "workspace_id": "5d2f7f3a-5db1-4f2c-8b9e-13607dd1f5e8"
    }
  }
}
```

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `error.code` | 예 | 클라이언트 분기용 안정 코드 |
| `error.message` | 예 | 사람에게 보여줄 수 있는 설명. 클라이언트가 파싱하지 않음 |
| `error.details` | 아니오 | 필드 오류, 리소스 식별자 등 부가 정보. 민감 정보 금지 |

`details`가 없으면 필드 자체를 생략합니다.

## Legacy compatible 에러 응답

기존 Go API를 그대로 이관하는 엔드포인트는 아래 형태를 유지합니다.

```json
{
  "error": "unauthorized"
}
```

이 모드에서도 서버 내부에서는 표준 에러 코드를 매핑할 수 있습니다. 단, public body에 `code`를
추가하는 것은 계약 변경으로 보고 별도 합의 후 진행합니다.

## 에러 코드 네이밍

에러 코드는 `DOMAIN_REASON` 형식의 `UPPER_SNAKE_CASE`를 사용합니다.

- 공통 에러는 `COMMON_` prefix를 사용합니다.
- 인증/인가 에러는 `AUTH_` prefix를 사용합니다.
- 도메인 에러는 도메인 이름을 prefix로 사용합니다. 예: `WORKSPACE_`, `MEMORY_`, `SOURCE_`.
- 같은 의미의 에러 코드는 HTTP status가 달라도 재사용하지 않습니다.
- 코드는 삭제하거나 의미를 바꾸지 않습니다. 필요하면 새 코드를 추가합니다.

## 공통 에러 코드

| HTTP status | Code | 사용 기준 |
| --- | --- | --- |
| 400 | `COMMON_BAD_REQUEST` | 요청 형식, 타입, JSON 파싱 오류 |
| 400 | `COMMON_VALIDATION_FAILED` | Bean Validation 등 필드 검증 실패 |
| 401 | `AUTH_UNAUTHORIZED` | 인증 정보 없음 |
| 401 | `AUTH_INVALID_TOKEN` | 토큰 파싱/검증 실패 |
| 403 | `AUTH_FORBIDDEN` | 인증은 되었지만 권한 없음 |
| 404 | `COMMON_NOT_FOUND` | 특정 도메인 코드가 아직 없을 때의 기본 not found |
| 405 | `COMMON_METHOD_NOT_ALLOWED` | 해당 경로에서 허용되지 않은 HTTP 메서드 |
| 409 | `COMMON_CONFLICT` | 중복, 상태 충돌 등 기본 conflict |
| 415 | `COMMON_UNSUPPORTED_MEDIA_TYPE` | 지원하지 않는 요청 Content-Type |
| 500 | `COMMON_INTERNAL_SERVER_ERROR` | 서버 내부 오류. 상세 원인 노출 금지 |
| 502 | `COMMON_BAD_GATEWAY` | 외부 서비스 호출 실패 |

도메인별 에러가 필요하면 공통 코드를 그대로 쓰기보다 의미가 드러나는 도메인 코드를 추가합니다.

예:

| HTTP status | Code | 사용 기준 |
| --- | --- | --- |
| 400 | `WORKSPACE_INVALID_SLUG` | workspace slug 형식이 유효하지 않음 |
| 409 | `WORKSPACE_SLUG_ALREADY_EXISTS` | workspace slug 중복 |
| 404 | `WORKSPACE_NOT_FOUND` | workspace를 찾을 수 없음 |
| 400 | `INVITATION_INVALID_TOKEN` | 초대 토큰 형식이 유효하지 않음 |
| 404 | `INVITATION_NOT_FOUND` | 초대를 찾을 수 없음 |
| 409 | `INVITATION_EXPIRED` | 초대가 만료됨 |

### 구현된 도메인 코드

| HTTP status | Code | 사용 기준 |
| --- | --- | --- |
| 401 | `AUTH_GOOGLE_TOKEN_INVALID` | Google ID 토큰 검증 실패 |
| 401 | `AUTH_GOOGLE_EMAIL_NOT_VERIFIED` | Google 계정 이메일이 검증되지 않음 |
| 401 | `AUTH_REFRESH_TOKEN_INVALID` | refresh token 형식·해시·만료·폐기 상태가 유효하지 않음 |
| 400 | `AUTH_OAUTH_STATE_INVALID` | 웹 OAuth 콜백의 state 불일치·누락 또는 code 누락 |
| 502 | `AUTH_OAUTH_EXCHANGE_FAILED` | 웹 OAuth code 교환·userinfo 조회 실패 |
| 404 | `USER_NOT_FOUND` | 사용자를 찾을 수 없음 (`GET/PATCH /api/me` 등) |
| 409 | `SIGNAL_INVALID_STATE` | 이미 다른 action으로 처리된 Signal에 다른 action을 요청함(같은 action 재요청은 200 멱등 응답) |

### 모바일 MVP 예정 도메인 코드

모바일 MVP API를 구현할 때는 다음 도메인 코드를 추가한다.

| HTTP status | Code | 사용 기준 |
| --- | --- | --- |
| 404 | `PROJECT_NOT_FOUND` | 프로젝트를 찾을 수 없음 |
| 404 | `SIGNAL_NOT_FOUND` | Signal을 찾을 수 없음 |
| 404 | `TASK_NOT_FOUND` | 태스크를 찾을 수 없음 |
| 404 | `TASK_CHECKLIST_ITEM_NOT_FOUND` | 태스크 체크리스트 항목을 찾을 수 없음 |

### 웹 Google 로그인(Authorization Code) 리다이렉트 계약

`GET /api/auth/google/login`·`GET /api/auth/google/callback`은 브라우저 리다이렉트(302)로 동작하므로
JSON 에러 본문을 쓰지 않습니다. 콜백 실패는 브라우저에 JSON을 노출하지 않도록 `failure-uri`로
리다이렉트하며 `?error=`에 다음 값 중 하나를 싣습니다(민감정보 미포함).

| `?error=` | 의미 | 매핑 에러 코드 |
| --- | --- | --- |
| `invalid_state` | state 불일치·누락 또는 code 누락 | `AUTH_OAUTH_STATE_INVALID` |
| `email_not_verified` | Google 이메일 미검증 | `AUTH_GOOGLE_EMAIL_NOT_VERIFIED` |
| `google_error` | code 교환·userinfo 실패 | `AUTH_OAUTH_EXCHANGE_FAILED` |
| `server_error` | 그 외 예기치 못한 오류 | (기타) |

성공 시 access/refresh를 HttpOnly 쿠키로 설정하고 `success-uri`로 리다이렉트합니다.

### 웹 쿠키 세션 갱신·로그아웃 계약

웹은 access 만료 시 refresh 쿠키로 회전 재발급하고 로그아웃 시 세션 쿠키를 정리합니다. 두 경로 모두
`/api/auth` 하위라 refresh 쿠키(`Path=/api/auth`)가 전송되며, same-domain 배포라 CSRF는 SameSite로
충족합니다(별도 토큰 없음). 토큰이 HttpOnly 쿠키에 있어 본문은 없습니다.

| 메서드·경로 | 성공 | 실패 |
| --- | --- | --- |
| `POST /api/auth/web/refresh` | `204` + `Set-Cookie`로 access/refresh 회전 | refresh 쿠키 없음·무효 → `401` `AUTH_REFRESH_TOKEN_INVALID` |
| `POST /api/auth/web/logout` | `204` + access/refresh 쿠키 정리(`Max-Age=0`) | 없음(쿠키 유무·상태와 무관하게 `204`, 멱등) |

모바일 JSON 계약(`POST /api/auth/refresh`·`/api/auth/logout`, body의 `refresh_token`)은 그대로
유지됩니다.

## Validation details

필드 검증 실패는 가능한 한 `details.fields`에 정리합니다.

```json
{
  "error": {
    "code": "COMMON_VALIDATION_FAILED",
    "message": "요청 값이 유효하지 않습니다.",
    "details": {
      "fields": [
        {
          "field": "email",
          "reason": "must be a well-formed email address"
        }
      ]
    }
  }
}
```

## HTTP status 사용 기준

- `200 OK`: 조회, 수정, 명령 성공 응답.
- `201 Created`: 리소스 생성 성공.
- `204 No Content`: 신규 엔드포인트에서 반환 body가 없기로 명시한 경우만 사용.
- `400 Bad Request`: 요청 형식/검증/도메인 입력 오류.
- `401 Unauthorized`: 인증 실패.
- `403 Forbidden`: 권한 없음.
- `404 Not Found`: 리소스 없음.
- `409 Conflict`: 이미 존재함, 상태 전이 불가, 만료/이미 처리됨 등 충돌.
- `500 Internal Server Error`: 서버 내부 오류.
- `502 Bad Gateway`: 외부 API 또는 downstream 서비스 호출 실패.

## 구현 시 체크리스트

- 기존 Go API 이관인지, 신규/개편 API인지 먼저 결정합니다.
- 기존 Go API 이관이면 status와 body shape를 기존과 맞춥니다.
- 신규/개편 API이면 Standard 에러 응답을 사용합니다.
- 새 에러 코드를 추가하면 이 문서의 코드 표 또는 도메인별 명세에 반영합니다.
- OpenAPI에 성공 예시와 주요 에러 예시를 함께 추가합니다.
