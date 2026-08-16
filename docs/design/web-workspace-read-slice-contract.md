# 첫 웹 read 수직 슬라이스 계약 (워크스페이스 조회)

상태: 구현 전 확정 계약

작성일: 2026-08-17

레거시 기준선: `Momens-Works/momens-api@71bbd07614fd2aef4dec726bafdf86c1bd097ba6`

관련 작업: `MOM-0850`

관련 문서: [이관 전략](legacy-product-api-migration-strategy.md),
[이관 원장](legacy-product-api-migration-ledger.md),
[API 버저닝](../spec/api-versioning.md),
[API 응답과 에러 코드](../spec/api-response-error-codes.md)

## 1. 목적

[이관 원장](legacy-product-api-migration-ledger.md)이 비교만 해둔 read-only 후보 중 첫 수직
슬라이스를 확정하고, 구현 전에 잠가야 할 계약을 고정한다. 이 문서가 확정한 뒤에야 구현 작업을
만든다.

## 2. 슬라이스 선정

첫 슬라이스는 **워크스페이스 조회(H020, H022)** 로 확정한다.

| 후보 | 판단 근거 |
| --- | --- |
| **워크스페이스 목록·상세 (H020, H022)** | **확정.** 레거시 응답 필드가 target 엔티티와 1:1로 일치하고, 계산 필드·조인·projection이 없다 |
| 프로젝트 목록·상세 (H038, H047) | 보류. 레거시 `label`, `owner_user_ids`, `health_status`, `progress`, `unresolved_count`, `voc_signal_count`, `last_context_at`, `metadata`가 target 엔티티에 없어 파생값 계산·보관 정책을 첫 슬라이스에서 함께 정해야 한다 |
| 마일스톤·태스크·결정/블로커 read | 보류. 원장의 판단을 유지한다 |

확정 근거는 다음 대조에서 나온다.

| 필드 | 레거시 `domain.Workspace` | target `Workspace` |
| --- | --- | --- |
| `id` | `uuid.UUID` | `BaseEntity.id` |
| `name` | `string` | `name` |
| `slug` | `string` | `slug` (unique) |
| `description` | `*string` (`omitempty`) | `description` (nullable) |
| `created_at` | `time.Time` | `BaseEntity.createdAt` |
| `updated_at` | `time.Time` | `BaseEntity.updatedAt` |

`workspaces` 테이블 Flyway는 이미 `:workspace` 모듈이 소유한다
(`V20260627090000__create_workspace.sql`). **이 슬라이스에 신규 DDL은 없다.**

또한 `MOM-0845`(모바일 워크스페이스 scope)는 이 슬라이스의 선행 조건이 아니다. 대상이 웹 계약이고
모바일 bootstrap 응답을 건드리지 않는다.

## 3. 레거시 트레이스

기준선 SHA에서 확인한 사실만 적는다.

| 단계 | 위치 | 확인 내용 |
| --- | --- | --- |
| route | `internal/bootstrap/router.go:93` | `r.Group("/workspaces")` + `Use(requireAuth)`. `GET ""` → `List`, `GET "/:id"` → `Get` |
| middleware | `internal/platform/httpx/middleware.go:72` | `session_token` 쿠키의 HS256 JWT. subject = `users.id`. 쿠키 없음 401 `{"error":"unauthorized"}`, 파싱 실패 401 `{"error":"invalid token"}` |
| handler | `internal/workspace/handler.go:93` | List: `{"workspaces": [...]}`, nil → `[]` 보장 |
| handler | `internal/workspace/handler.go:111` | Get: 래퍼 없는 단일 객체. path id 파싱 실패 400. **그 밖의 모든 실패를 403으로 반환** |
| service | `internal/workspace/service.go:166` | List는 권한 검사 없이 멤버십 조인 결과를 그대로 반환 |
| service | `internal/workspace/service.go:170` | Get은 `RequireMember` 통과 후 `GetByID` |
| repository | `internal/workspace/repository.go:59` | `workspaces JOIN workspace_members ON user_id = $1`, `ORDER BY w.created_at DESC` |
| repository | `internal/workspace/repository.go:47` | `SELECT ... FROM workspaces WHERE id = $1`. **soft-delete 컬럼·필터 없음** |
| schema | `migrations/000001_init.sql` | `workspaces`, `workspace_members` |
| test | `internal/workspace/` | **List·Get characterization 테스트 없음.** 기존 테스트는 slug 수명주기, seed, onboarding, invitation |

레거시 전용 테스트가 없으므로 **이 문서가 계약의 출처**이고, 검증 오라클은 `../e2e` 대조 스위트다.

## 4. 잠근 계약

### 4.1 path와 버저닝

[API 버저닝](../spec/api-versioning.md)과 ADR-0006을 그대로 따른다. 레거시 path alias는 두지 않는다.

| 레거시 | 신규 |
| --- | --- |
| `GET /workspaces` | `GET /api/workspaces` (`version = "1"`) |
| `GET /workspaces/:id` | `GET /api/workspaces/{workspaceId}` (`version = "1"`) |

### 4.2 인증

신규 서버 기준으로 맞춘다(이관 전략 결정②A). 보호 체인이 Bearer 헤더 또는 `access_token` 쿠키에서
access token을 읽고, 컨트롤러는 `CurrentUser.id(principal)`로 `users.id`를 받는다. 레거시
`session_token` 쿠키를 신규 서버가 읽는 fallback은 두지 않는다.

레거시와 신규 토큰 모두 HS256이고 subject가 `users.id`이며 DB를 공유하므로, 사용자 식별은 추가 매핑
없이 일치한다.

### 4.3 응답 body

성공 응답은 레거시 shape를 유지한다. 전역 wrapper는 쓰지 않는다.

`GET /api/workspaces` — 200

```json
{
  "workspaces": [
    {
      "id": "5d2f7f3a-5db1-4f2c-8b9e-13607dd1f5e8",
      "name": "Momens",
      "slug": "momens",
      "description": "제품팀 워크스페이스",
      "created_at": "2026-06-27T09:00:00Z",
      "updated_at": "2026-06-27T09:00:00Z"
    }
  ]
}
```

- 빈 결과는 `null`이 아니라 `[]`다.
- 정렬은 `created_at` 내림차순이다.
- 요청자가 멤버인 워크스페이스만 포함한다. 별도 권한 검사 없이 멤버십 조인이 곧 필터다.

`GET /api/workspaces/{workspaceId}` — 200: 위 배열 원소와 같은 객체를 래퍼 없이 반환한다.

`description`은 값이 없으면 필드를 생략한다(레거시 `omitempty` 동작 유지).

soft-delete 필터는 두지 않는다. 레거시에 해당 컬럼과 필터가 없어 재현할 동작이 없다.

### 4.4 에러 응답

**Standard 모드**를 사용한다([API 응답과 에러 코드](../spec/api-response-error-codes.md)).

전환에 클라이언트가 path와 `API-Version` 헤더를 함께 바꾸는 배포가 필요하므로, 레거시 body를
보존해서 얻는 호환 이득이 없다. 또한 레거시 Get은 미존재와 권한 없음을 구분하지 않고 Go의 raw error
문자열을 그대로 실어 보내, 내부 예외 메시지를 노출하지 않는다는 응답 규격과 충돌한다.

| 상황 | 레거시 | 신규 |
| --- | --- | --- |
| 인증 없음·토큰 무효 | 401 `{"error":"unauthorized"}` / `{"error":"invalid token"}` | 401 `AUTH_UNAUTHORIZED` / `AUTH_INVALID_TOKEN` |
| path id가 UUID가 아님 | 400 `{"error":"invalid workspace id"}` | 400 `COMMON_BAD_REQUEST` |
| 워크스페이스 없음 | 403 (raw error 문자열) | **404 `WORKSPACE_NOT_FOUND`** |
| 멤버가 아님 | 403 (raw error 문자열) | **403 `AUTH_FORBIDDEN`** |

`WORKSPACE_NOT_FOUND`는 이미 [에러 코드표](../spec/api-response-error-codes.md)에 있다.
미존재와 권한 없음을 나누면 워크스페이스 존재 여부가 드러나지만, 식별자가 추측 불가능한 UUID라
열거 위험이 없다고 판단한다.

## 5. Target 설계

모듈은 `:workspace`로 확정한다. capability 모듈이 자기 도메인의 HTTP 입출력을 소유하고, 조회
결과를 모듈 밖으로 공개하지 않아 reader를 `internal`에 닫아둘 수 있다. 웹 표면 전용 모듈은 만들지
않는다.

```text
modules/workspace/src/main/java/works/momens/server/workspace/
├── WorkspaceErrorCode.java              # 신규. WORKSPACE_NOT_FOUND
├── presentation/
│   ├── WorkspaceController.java         # 신규. /api/workspaces, version "1"
│   ├── WorkspaceControllerDocs.java     # 신규. OpenAPI
│   └── dto/response/
│       ├── WorkspaceResponse.java       # 신규
│       └── WorkspaceListResponse.java   # 신규
└── internal/
    ├── WorkspaceReader.java             # 신규. 목록·단건 조회
    └── WorkspaceRepository.java         # 변경. 조회 메서드 2개 추가
```

- `WorkspaceRepository`에 멤버십 조인 목록 조회와 단건 조회를 추가한다. 정렬은 쿼리에서 고정한다.
- 권한 판정은 `WorkspaceAccess.isMember`를 재사용한다. 이 API는 `boolean`만 반환하므로 에러 선택은
  호출하는 쪽에서 한다.
- 목록은 멤버십 조인이 필터라 별도 권한 검사를 하지 않는다.
- 쓰기가 없어 트랜잭션 경계와 outbox는 이 슬라이스의 범위 밖이다.
- `:workspace` `build.gradle`에 `spring-boot-starter-webmvc`(main)와
  `spring-boot-starter-webmvc-test`(test)를 추가하고, 모듈 테스트 리소스에 전역과 같은
  `SNAKE_CASE` 설정을 둔다. springdoc은 `app`이 소유하므로 모듈에 추가하지 않는다.

## 6. 전환과 롤백

ingress는 `API-Version` 헤더 유무로 신규 서버와 레거시를 분기한다. path rewrite는 하지 않는다.

이 방식에서 전환 단위는 **클라이언트 배포**다. 웹이 path(`/workspaces` → `/api/workspaces`)와
`API-Version` 헤더를 함께 바꿔야 신규 서버로 넘어가고, 되돌리는 것도 클라이언트 배포다. 서버는
두 endpoint를 추가할 뿐이며 레거시 endpoint는 그대로 살아 있다.

쓰기·projection이 없어 데이터 보상 절차는 필요 없다.

## 7. 미결정 사항

구현 중 조용히 정하지 않는다.

1. 레거시 미들웨어의 토큰 추출 fallback(`Authorization` 헤더·`access_token` 쿠키) 반영 시점.
   기준선 SHA 기준으로 아직 반영되지 않았고, 이 슬라이스의 선행 조건은 아니다.
2. 신규 endpoint로 전환한 뒤 레거시 `GET /workspaces`, `GET /workspaces/:id`를 retire하는 시점.

## 8. 후속 작업

1. `MOM-0851` [Feat] 웹 워크스페이스 조회 endpoint 이관 (H020, H022) — 위 계약대로 구현과 테스트
2. 구현 뒤 원장의 H020·H022 상태를 `contract_locked`에서 `implemented`로 갱신
