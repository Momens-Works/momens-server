# 서버 명세

서버가 외부와 맺는 계약을 정리합니다.

| 문서 | 내용 |
| --- | --- |
| [API 버저닝](api-versioning.md) | Spring API versioning 적용 대상, 버전 전달 방식, breaking change 기준 |
| [OpenAPI](openapi.md) | Swagger/OpenAPI 문서화 방식, 성공/실패 예시, controller docs interface 규칙, 스냅샷 고정 |
| [OpenAPI 스냅샷](openapi.json) | `/v3/api-docs` 커밋본. 직접 편집하지 않고 `./gradlew updateOpenApiSnapshot`으로 갱신합니다 |
| [API 응답과 에러 코드](api-response-error-codes.md) | 성공 응답 형태, 에러 body, 에러 코드 네이밍과 HTTP status 매핑 |
| [모바일 API 명세](mobile-api.md) | 모바일 MVP HTTP API 계약과 request/response 예시 |
