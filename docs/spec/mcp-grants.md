# MCP 연결 조회·폐기 웹 API

MOM-0999는 레거시 H035·H036을 신규 서버로 이관한다. HTTP 표면은 `:web`, grant와 token
lifecycle은 `:mcp`가 소유한다([ADR-0024](../adr/0024-mcp-grant-web-surface.md)).

## 인증과 범위

- Momens 사용자 access token(Bearer 또는 기존 웹 쿠키)으로 인증한다. MCP reference token은
  이 API 인증에 사용할 수 없다. `API-Version`은 `1`이다.
- 두 API 모두 현재 workspace membership이 필요하다. 자기 자신이 승인한 연결만 조회·폐기한다.
  owner/admin도 다른 사용자의 연결을 폐기할 수 없다.
- 기존 grant/client/token은 이전하지 않는다. 신규 연결만 조회한다. FE base 전환과 운영
  canonical cutover는 MOM-0967·MOM-0992에서 수행한다.

## 목록

`GET /api/workspaces/{workspaceId}/mcp-grants`

성공은 `200`과 `{ "grants": [...] }`다. 활성 연결을 생성 시각 내림차순, 동일 시각에는
ID 오름차순으로 반환한다. 각 항목의 `id`, `client_id`, `client_name`, `user_id`,
`workspace_id`, `scopes`, `created_at`은 레거시 필드를 유지한다.

- `last_used_at`은 성공한 MCP token 검증 시각(UTC)이고 사용 전에는 생략한다.
- `revoked_at`은 활성 연결만 반환하므로 레거시와 같이 생략한다.
- 빈 목록은 프로젝트 목록 규칙에 맞춰 `[]`로 반환한다. 레거시는 `null`을 반환할 수 있었고,
  현재 FE는 두 형태를 모두 수용한다.
- 등록 client가 없으면 레거시 JOIN과 같이 목록에서 제외한다. 저장소 조회 장애는 정상 빈
  목록으로 바꾸지 않는다.

```json
{
  "grants": [{
    "id": "b1e62c45-c6e9-4919-a2ed-cc4f1befa9af",
    "client_id": "public-client-id",
    "client_name": "MCP client",
    "user_id": "c15d1384-6cf5-438c-a08e-48175995c29c",
    "workspace_id": "bdca1b6a-2c3e-4bfc-81b6-a726e16d9d9e",
    "scopes": ["mcp:tasks:read"],
    "last_used_at": "2026-09-28T03:00:00Z",
    "created_at": "2026-09-28T02:00:00Z"
  }]
}
```

## 폐기

`DELETE /api/workspaces/{workspaceId}/mcp-grants/{grantId}`

성공은 레거시와 같은 `204`, 본문 없음이다. grant와 연결된 모든 token family의
access/refresh token을 같은 트랜잭션에서 무효화한다. 토큰 저장이 실패하면 grant 폐기도
rollback한다. 커밋 후 해당 token의 MCP 접근·refresh를 거부하며 사용자 로그인 세션과
다른 연결은 유지한다.

없는 연결, 이미 폐기한 연결, 다른 사용자·workspace의 연결은 모두 `403`이다. 삭제 재시도를
성공으로 바꾸지 않는다. 레거시에서 빠져 있던 폐기 시점의 membership 검사를 추가한다.

## 오류

| 조건 | HTTP | 본문 |
| --- | --- | --- |
| 잘못된 workspace UUID | 400 | `{ "error": "invalid workspace id" }` |
| 잘못된 grant UUID | 400 | `{ "error": "invalid grant id" }` |
| 비멤버 또는 연결 접근 불가 | 403 | `{ "error": "forbidden" }` |
| 사용자 미인증·무효 토큰 | 401 | 기존 사용자 인증 필터의 Standard 에러 |
| 내부 저장소 장애 | 500 | 기존 전역 핸들러의 Standard 에러, 내부 정보 비노출 |

예상 입력·인가 오류는 레거시 shape를 유지한다. 레거시의 예상치 못한 저장소 오류를
`400 invalid_interaction`으로 바꾸는 동작은 복제하지 않고 서버 장애로 응답한다.

## 사용 시각과 스키마

`V20260928120000__add_mcp_grant_last_used_at.sql`이 nullable `timestamptz`를 추가한다.
기존 행은 `null`로 시작한다. 사용 시각 보존은 MOM-0999 착수 시 사용자 확인으로 확정했다.

reference token·resource·grant·scope·membership 검증을 모두 통과한 뒤 사용 시각을 기록한다.
목록 조회, 실패한 인증, OAuth 갱신만으로는 기록하지 않는다. 원자적 컬럼 UPDATE로 시간이
뒤로 가지 않게 하고, 폐기된 행에는 기록하지 않는다. grant 폐기는 변경 컬럼만 UPDATE해
동시 사용 시각 갱신을 덮어쓰지 않는다. 각 성공 인증에 DB 쓰기 한 번이 추가된다.

## 검증

실제 PostgreSQL과 HTTP OAuth 흐름으로 사용자·workspace 격리, 사용 시각, 폐기 후
access/refresh 거부, 폐기 저장 실패 시 rollback을 검증한다. verifier 단위 테스트는
거부된 인증에서 사용 시각 기록을 호출하지 않는지도 검사한다.
