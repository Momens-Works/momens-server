# 레거시 Product API 이관 원장

상태: Draft

조사일: 2026-08-14

레거시 기준선: `Momens-Works/momens-api@71bbd07614fd2aef4dec726bafdf86c1bd097ba6`

관련 작업: `MOM-0848`

## 목적과 읽는 법

이 원장은 [레거시 Product API 이관 전략](legacy-product-api-migration-strategy.md)의 상태 원장이다.
레거시의 HTTP route declaration 96개와 HTTP 밖의 실행 진입점을 코드 기준으로 추적한다.

HTTP 행은 `H001`부터, 비-HTTP 행은 `N001`부터 식별한다. 각 행의 필수 필드는 다음 세 곳을
합쳐 읽는다.

1. **진입점 행**: surface, legacy entry point, handler, 읽기/쓰기, 상태와 행별 예외
2. **trace profile**: handler 이후 service/repository/model/migration/test와 target
3. **전환 profile**: contract, auth/RBAC, writer, projection/external, prod/client gate,
   rollback, task/PR

행에서 profile을 참조하는 것은 필드 생략이 아니다. 같은 capability의 반복 정보를 한 profile에
고정해 route 사이에서 서로 다른 판단이 섞이지 않게 한다. 행의 메모가 profile과 다르면 행의 메모를
우선한다.

상태는 전략의 `traced`, `contract_locked`, `implemented`, `cutover_ready`, `cutover`, `retired`만
사용한다. `implemented`는 신규 코드가 있다는 뜻일 뿐 운영 트래픽이 전환됐다는 뜻이 아니다.

모든 행은 별도 표기가 없으면 다음 공통 값을 가진다.

| 필드 | 공통 값 |
| --- | --- |
| legacy baseline | `71bbd07614fd2aef4dec726bafdf86c1bd097ba6` |
| trace task | `MOM-0848` |
| implementation task/PR | 미생성. 후보 선정·계약 잠금 뒤 별도 작업으로 연결 |
| current traffic owner | `momens-api` |

## 기준선과 전수성 검증

조사 시작 시 레거시 저장소에서 다음을 실행했다. fetch 전후 `origin/main` SHA는 같았고 작업 트리는
깨끗했다.

```bash
git -C ../momens-api fetch origin main
git -C ../momens-api rev-parse origin/main
# 71bbd07614fd2aef4dec726bafdf86c1bd097ba6

rg -n '\.(GET|POST|PATCH|DELETE|PUT|Any)\(' \
  ../momens-api/internal/bootstrap/router.go
# 96 declarations
```

96개는 조건부 등록을 포함한 **소스 선언 수**다. `r.Any("/mcp", ...)`는 선언 하나로 세며 Gin이
런타임에 여러 HTTP method로 확장하는 수는 중복해서 세지 않는다.

| surface | 선언 수 |
| --- | ---: |
| Product JSON API | 75 |
| Product 인증 | 5 |
| OAuth protocol·grant 관리 | 12 |
| MCP transport | 1 |
| 외부 provider callback | 1 |
| Slack webhook | 1 |
| 운영 표면 | 1 |
| **합계** | **96** |

HTTP 밖 표면은 다음 방법을 함께 사용했다.

```bash
rg -n 'mcp\.AddTool' ../momens-api/internal/mcpserver
rg -n 'go func|time\.NewTicker|RunMigrations|BackfillSearchTokens' \
  ../momens-api --glob '*.go' --glob '!**/*_test.go'
rg --files ../momens-api/cmd
```

그 결과 MCP tool 11개, startup migration runner, retrieval backfill·embedding loop, Slack 비동기
처리, HTTP server lifecycle, 오프라인 CLI 3개를 확인했다. 별도 cron/scheduler 등록은 없었다.

현재 HTTP 상태는 `implemented` 6개(H001, H014~H018), `traced` 90개다. 비-HTTP·tool 19개는
모두 `traced`다. `cutover_ready` 이상인 항목은 없다.

## 공통 전환 규칙

- Product JSON API의 target path는 `/api`, handler version은 `1`이며 레거시 root path alias는
  만들지 않는다.
- Product JSON API는 별도 합의 전까지 `Legacy compatible`이다. status와 성공·실패 body shape를
  characterization test로 고정한다.
- 레거시 보호 route는 `session_token` 쿠키 JWT를 사용한다. 신규 웹은
  `access_token`·`refresh_token` HttpOnly 쿠키를 사용한다. 혼합 트래픽을 쓰지 않는 한 세션
  브리지는 만들지 않는다.
- 모든 Product JSON route에는 FE의 path·`API-Version: 1`·세션 전환이 client gate로 걸린다.
- read-only route의 기본 rollback은 routing rollback이다. write route는 신규 데이터가 레거시
  schema·enum·relation·projection과 호환된다는 증거가 있기 전까지 writer rollback을 보장하지 않는다.
- aggregate별 writer는 한 시점에 하나만 둔다. REST, MCP tool, Slack action, webhook,
  background runtime을 함께 계산한다.
- prod schema와 설정의 전역 release gate는 [prod 운영 준비 대장](../prod-schema-ledger.md)에서
  확인한다. 이 게이트는 trace·local/dev 구현의 선행 조건이 아니다.

## Trace profile

경로는 모두 레거시 `momens-api` 루트 기준이다. `H/S/R`은 handler/service/repository를 뜻한다.

| Profile | capability·legacy trace | schema·test | target |
| --- | --- | --- | --- |
| `OP` | `bootstrap/router.go`의 inline health handler, `cmd/api/main.go`, `bootstrap/app.go` | DB 없음; `bootstrap/router_test.go`, `bootstrap/app_test.go` | `app`의 Actuator health |
| `MOA-P` | `mcpauth/handler.go` → `service.go` → `repository.go` → `model.go` | `000019_mcp_oauth.sql`; `mcpauth/handler_test.go`, `service_test.go`, `repository_test.go` | **미결**: `auth` 또는 별도 MCP/OAuth 경계 |
| `MOA-I` | `mcpauth/handler.go`의 interaction 조회·승인·거절 → 같은 service/repository/model | `000019_mcp_oauth.sql`, `users`, `workspace_members`; `mcpauth/*_test.go` | **미결**: MCP/OAuth 경계, 사용자 세션은 `auth`·`user`·`workspace` public API 사용 |
| `MOA-G` | `mcpauth/handler.go`의 grant 목록·폐기 → 같은 service/repository/model | `000019_mcp_oauth.sql`; `mcpauth/*_test.go` | **미결**: MCP/OAuth 경계 |
| `MCP` | `mcpserver/server.go`, `auth.go`, `tools.go`, `milestones.go`, `helpers.go` → `project`·`milestone`·`task`·`workspace` service/repository → `domain/models.go` | `000001_init.sql`, `000006_fe_contract.sql`, `000012_task_updates.sql`, `000017_project_label.sql`, `000019_mcp_oauth.sql`; `mcpserver/server_test.go`, `milestones_test.go` | **미결**: transport/tool 소유 경계. 도메인 write는 `workspace`·`project` public API로 위임 |
| `SLK` | `slackbot/handler.go`, `events.go`, `answerer.go`, `grounded.go`, `action.go` → `minsu/service.go`, `minsu/action/*`, `project`·`task`·`workspace` service/repository | task·retrieval schema; `slackbot/*_test.go`, `minsu/action/*_test.go` | `minsu`가 Slack 표면 흡수, task write는 `project` public API 사용 |
| `AUT` | `auth/handler.go` → `auth/service.go` → `auth/repository.go` → `domain.User`, `platform/auth/jwt.go`, `platform/oauth/google.go` | `000001_init.sql`, `000007_user_job_role.sql`, `000018_refresh_tokens.sql`; `auth/service_integration_test.go`, `platform/auth/jwt_test.go` | `auth` 세션과 `user` 신원·프로필 |
| `USR` | legacy `auth/handler.go`의 `Me`·`UpdateMe` → `auth/service.go` → `auth/repository.go` → `domain.User` | `000001_init.sql`, `000007_user_job_role.sql`; `auth/service_integration_test.go` | `user`; `/api/me` GET/PATCH 구현 완료 |
| `WSP` | `workspace/handler.go` → `service.go` → `repository.go`, `access/service.go`·`repository.go`, `label/label.go` → workspace 관련 `domain/models.go` | `000001_init.sql`, `000006_fe_contract.sql`, `000009_member_onboarding_state.sql`, `000011_workspace_invitations.sql`; `workspace/*_test.go`, `access/service_integration_test.go` | `workspace` |
| `SNP` | `snapshot/handler.go` → workspace/project/milestone/task/blocker/memory/relation service·repository | 위 capability의 모든 schema; `snapshot/handler_integration_test.go` | 단일 모듈 소유 미확정. 얇은 웹 orchestration 표면 후보 |
| `PRJ` | `project/handler.go` → `service.go` → `repository.go` → `domain.Project`, `access` | `000001_init.sql`, `000006_fe_contract.sql`, `000008_project_metadata.sql`, `000017_project_label.sql`; `project/handler_test.go`, `service_integration_test.go` | `project` |
| `MIL` | `milestone/handler.go` → `service.go` → `repository.go` → `domain.Milestone`, `access` | `000001_init.sql`, `000006_fe_contract.sql`; `milestone/service_integration_test.go` | `project` |
| `TSK` | `task/handler.go` → `service.go` → `repository.go` → `domain.Task`·`TaskUpdate`, `retrieval/projection.go`·`repository.go` | `000001_init.sql`, `000002_retrieval_projection.sql`, `000006_fe_contract.sql`, `000012_task_updates.sql`, `000013_task_workspace_cascade.sql`; `task/service_integration_test.go`, `retrieval/projection_test.go` | `project`의 nested `task`; 웹 계약은 `MOM-0773` 필요 |
| `DEC` | `decision/handler.go` → `service.go` → `repository.go` → `domain.Decision`, `retrieval/projection.go` | `000001_init.sql`, `000002_retrieval_projection.sql`; 전용 test 없음 | `project` |
| `BLK` | `blocker/handler.go` → `service.go` → `repository.go` → `domain.Blocker`, `retrieval/projection.go` | `000001_init.sql`, `000002_retrieval_projection.sql`; 전용 test 없음 | `project` |
| `SRC` | `source/handler.go` → `service.go` → `repository.go`, `oauth.go`, `figma.go` → source 관련 `domain/models.go` | `000002_retrieval_projection.sql`, `000004_source_connections.sql`, `000006_fe_contract.sql`, `000010_source_credentials.sql`, `000015_source_refs_content_hash.sql`; `source/service_integration_test.go`, `oauth_test.go`, `figma_test.go` | `source`; ingest는 `momens-worker` |
| `MEM` | `memory/candidate_handler.go`, `memory/memory_handler.go` → `service.go` → `repository.go` → memory 관련 `domain/models.go`, `retrieval/projection.go` | `000003_memory.sql`, `000006_fe_contract.sql`, `000002_retrieval_projection.sql`; `memory/service_integration_test.go` | `memory` |
| `CTX` | `relation/handler.go` → `service.go` → `repository.go` → `domain.EntityRelation`·`SourceRef` | `000002_retrieval_projection.sql`; 전용 test 없음 | `context`; source-ref 원본 조회는 `source` public API 사용 |
| `MIN` | `minsu/handler.go` → `service.go`, `synthesis.go`, `embedding.go` → `retrieval/client.go`와 LLM adapter | 자체 write schema 없음; `minsu/service_test.go`, `embedding_test.go`, retrieval client tests | `minsu` query 유스케이스. 기존 task draft API DTO와 분리 |

## 전환 profile

| Profile | contract·auth/RBAC | writer·projection/external | gate·rollback·task |
| --- | --- | --- | --- |
| `OP` | 운영 계약. 공개 health | read-only, DB 없음 | 신규 `/actuator/health`는 구현됨. ingress/probe 전환과 routing rollback 필요. `MOM-0848` |
| `MOA-P` | OAuth metadata·등록·인가·token·revoke protocol 계약. endpoint별 client/token 검증 | `oauth_*` writer; OAuth client와 MCP client 외부 의존 | `oauth_*` prod 존재와 target module 미확정. protocol client rollback/runbook 필요. 후속 MCP/OAuth 결정 작업 |
| `MOA-I` | legacy `session_token` 인증 후 interaction user/workspace 검증 | `oauth_interactions`, grant/code writer | 신규 웹 세션과 consent UI 동시 전환 필요. writer rollback 미확정. 후속 MCP/OAuth 결정 작업 |
| `MOA-G` | legacy 세션 + workspace membership/grant ownership | `oauth_grants` writer | grant/token drain·폐기 정책 필요. 후속 MCP/OAuth 결정 작업 |
| `MCP` | MCP Streamable HTTP, OAuth bearer와 tool별 scope | project/milestone/task/comment writer가 REST와 같은 aggregate를 공유 | MCP client 재등록, grant/token 전환, 단일 writer와 rollback 필요. 후속 MCP/OAuth 결정 작업 |
| `SLK` | Slack signature·retry·3초 ack 계약 | retrieval·Vertex·Slack API; action layer가 task writer | signing secret, bot identity, redirect/event URL, 비동기 실패 관측 필요. task writer·projection과 함께 전환. 후속 Slack 표면 작업 |
| `AUT` | 레거시 단일 JWT 대신 확정된 Standard 웹 access+refresh 쿠키 계약 | `users`, `user_identities`, `refresh_tokens`; Google OAuth | 신규 경로 구현 완료(`MOM-0640`, `MOM-0641`). FE 로그인과 함께 전환하며 레거시 세션 rollback은 별도 브리지 없이는 불가 |
| `USR` | 신규 `/api/me` Standard 계약, 신규 cookie/Bearer 공통 인증 | `users` writer | 구현 완료(`MOM-0632`, `MOM-0635`). legacy wrapper와 field 차이 characterization 및 FE 전환 필요 |
| `WSP` | Product JSON 기본 규칙, legacy 세션 + membership/owner/admin RBAC | `workspaces`, member, invitation, label sequence writer; invitation email | `MOM-0845` workspace scope 영향. read는 routing rollback, write는 schema/label/email 호환 확인 전 rollback 미보장 |
| `SNP` | Product JSON 기본 규칙, workspace membership | read-only 합성; 여러 aggregate·relation 조회 | read routing rollback 가능. 응답 필드별 target owner와 N+1/latency 계약을 먼저 고정. `MOM-0848` 후속 |
| `PRJ` | Product JSON 기본 규칙, workspace membership; mutation은 legacy service가 owner 권한 검사 | `projects`, `project_owners`; projection 없음 | `MOM-0845`와 legacy 전용 field/owner/progress 정책 확인. read routing rollback, write rollback 미확정 |
| `MIL` | Product JSON 기본 규칙, project workspace membership | `milestones`, `milestone_owners`; projection 없음 | target entity/API 미구현. read routing rollback, write rollback 미확정 |
| `TSK` | Product JSON 기본 규칙, project workspace membership | `tasks`, `task_updates`; task write는 retrieval projection 동반 | `MOM-0773`, worker outbox consumer와 task projector가 `cutover_ready` gate. write rollback 미확정 |
| `DEC` | Product JSON 기본 규칙, project workspace membership | `decisions`; write는 retrieval projection 동반 | 전용 legacy test가 없어 characterization 우선. worker decision projector가 write gate |
| `BLK` | Product JSON 기본 규칙, workspace membership | `blockers`; write는 retrieval projection 동반 | 전용 legacy test가 없어 characterization 우선. worker blocker projector가 write gate |
| `SRC` | Product JSON 또는 provider callback 계약. 보호 route는 membership, callback은 signed state | source connection/credential/sync state/source-ref writer; GitHub·Slack·Notion·Figma | provider redirect URI·secret·webhook·worker 호환 필요. `MOM-0774`가 source_ref 관계 계약에 영향 |
| `MEM` | Product JSON 기본 규칙, workspace membership | candidate/memory/review action writer; confirmed memory write는 retrieval projection 동반 | worker producer와 memory projector가 write gate. read routing rollback, write rollback 미확정 |
| `CTX` | Product JSON 기본 규칙, task workspace membership | `entity_relations`, 일부 `source_refs` writer | `MOM-0774`, source-ref 생산자·relation 호환 확인. read routing rollback, write rollback 미확정 |
| `MIN` | Product JSON 기본 규칙, workspace membership | domain write 없음; retrieval gRPC, Vertex AI | timeout·fallback·permission context·model 설정 contract lock 필요. read routing rollback 가능 |

## HTTP 진입점 원장

`R`은 read-only, `W`는 상태·쿠키·외부 side effect를 변경, `RW`는 하나의 transport 아래 양쪽이
공존함을 뜻한다. Product JSON 행은 별도 메모가 없으면 `Legacy compatible`이며 모두
`MOM-0848`에서 `traced`됐다.

| ID | surface | legacy entry point | handler | profile | mode | status·메모 |
| --- | --- | --- | --- | --- | --- | --- |
| H001 | operational | `GET /health` | inline | `OP` | R | `implemented`: target은 `/actuator/health`, 아직 probe 전환 아님 |
| H002 | OAuth | `GET /.well-known/oauth-authorization-server` | `AuthorizationServerMetadata` | `MOA-P` | R | `traced`; 조건부 등록 |
| H003 | OAuth | `GET /.well-known/oauth-protected-resource` | `ProtectedResourceMetadata` | `MOA-P` | R | `traced`; 조건부 등록 |
| H004 | OAuth | `GET /.well-known/oauth-protected-resource/mcp` | `ProtectedResourceMetadata` | `MOA-P` | R | `traced`; 조건부 등록 |
| H005 | OAuth | `POST /oauth/register` | `Register` | `MOA-P` | W | `traced`; dynamic client registration |
| H006 | OAuth | `GET /oauth/authorize` | `Authorize` | `MOA-P` | W | `traced`; consent interaction 생성·redirect |
| H007 | OAuth | `POST /oauth/token` | `Token` | `MOA-P` | W | `traced`; code 교환·refresh 회전 |
| H008 | OAuth | `POST /oauth/revoke` | `Revoke` | `MOA-P` | W | `traced` |
| H009 | OAuth | `GET /oauth/interactions/:id` | `GetInteraction` | `MOA-I` | R | `traced`; legacy 세션 필요 |
| H010 | OAuth | `POST /oauth/interactions/:id/approve` | `Approve` | `MOA-I` | W | `traced`; grant/code 생성 |
| H011 | OAuth | `POST /oauth/interactions/:id/deny` | `Deny` | `MOA-I` | W | `traced` |
| H012 | MCP | `ANY /mcp` | `mcpserver.Server` | `MCP` | RW | `traced`; 조건부 등록, 선언 하나로 계산 |
| H013 | webhook | `POST /slack/events` | `slackbot.Events` | `SLK` | RW | `traced`; 조건부 등록, signed webhook |
| H014 | Product auth | `GET /auth/google/login` | `auth.GoogleLogin` | `AUT` | W | `implemented`: target `/api/auth/google/login`, state+PKCE 계약으로 교체 |
| H015 | Product auth | `GET /auth/google/callback` | `auth.GoogleCallback` | `AUT` | W | `implemented`: target `/api/auth/google/callback`, access+refresh 발급 |
| H016 | Product auth | `POST /auth/logout` | `auth.Logout` | `AUT` | W | `implemented`: target `/api/auth/web/logout`; legacy message body는 폐기 합의됨 |
| H017 | Product auth | `GET /auth/me` | `auth.Me` | `USR` | R | `implemented`: target `GET /api/me`; cutover 전 |
| H018 | Product auth | `PATCH /auth/me` | `auth.UpdateMe` | `USR` | W | `implemented`: target `PATCH /api/me`; cutover 전 |
| H019 | Product JSON | `POST /workspaces` | `workspace.Create` | `WSP` | W | `traced` |
| H020 | Product JSON | `GET /workspaces` | `workspace.List` | `WSP` | R | `traced` |
| H021 | Product JSON | `GET /workspaces/slug-available` | `workspace.SlugAvailable` | `WSP` | R | `traced` |
| H022 | Product JSON | `GET /workspaces/:id` | `workspace.Get` | `WSP` | R | `traced` |
| H023 | Product JSON | `GET /workspaces/:id/snapshot` | `snapshot.Get` | `SNP` | R | `traced`; multi-capability 합성 |
| H024 | Product JSON | `PATCH /workspaces/:id` | `workspace.Update` | `WSP` | W | `traced` |
| H025 | Product JSON | `GET /workspaces/:id/onboarding` | `workspace.GetOnboarding` | `WSP` | R | `traced` |
| H026 | Product JSON | `PATCH /workspaces/:id/onboarding` | `workspace.PatchOnboarding` | `WSP` | W | `traced` |
| H027 | Product JSON | `GET /workspaces/:id/members` | `workspace.ListMembers` | `WSP` | R | `traced` |
| H028 | Product JSON | `POST /workspaces/:id/invite` | `workspace.Invite` | `WSP` | W | `traced`; 즉시 멤버 추가 legacy 경로 |
| H029 | Product JSON | `POST /workspaces/:id/invitations` | `workspace.CreateInvitation` | `WSP` | W | `traced`; invitation email side effect |
| H030 | Product JSON | `GET /workspaces/:id/invitations` | `workspace.ListInvitations` | `WSP` | R | `traced` |
| H031 | Product JSON | `POST /workspaces/:id/invitations/:invitationId/resend` | `workspace.ResendInvitation` | `WSP` | W | `traced`; email side effect |
| H032 | Product JSON | `POST /workspaces/:id/invitations/:invitationId/revoke` | `workspace.RevokeInvitation` | `WSP` | W | `traced` |
| H033 | Product JSON | `PATCH /workspaces/:id/members/:userId` | `workspace.UpdateMember` | `WSP` | W | `traced` |
| H034 | Product JSON | `DELETE /workspaces/:id/members/:userId` | `workspace.RemoveMember` | `WSP` | W | `traced` |
| H035 | OAuth | `GET /workspaces/:id/mcp-grants` | `mcpauth.ListGrants` | `MOA-G` | R | `traced`; 조건부 등록 |
| H036 | OAuth | `DELETE /workspaces/:id/mcp-grants/:grantId` | `mcpauth.RevokeGrant` | `MOA-G` | W | `traced`; 조건부 등록 |
| H037 | Product JSON | `POST /workspaces/:id/projects` | `project.Create` | `PRJ` | W | `traced` |
| H038 | Product JSON | `GET /workspaces/:id/projects` | `project.List` | `PRJ` | R | `traced` |
| H039 | Product JSON | `GET /workspaces/:id/blockers` | `blocker.List` | `BLK` | R | `traced` |
| H040 | Product JSON | `GET /workspaces/:id/source-connections` | `source.List` | `SRC` | R | `traced` |
| H041 | Product JSON | `GET /workspaces/:id/source-connections/install` | `source.Install` | `SRC` | W | `traced`; provider authorize redirect 시작 |
| H042 | Product JSON | `GET /workspaces/:id/memory-candidates` | `candidate.List` | `MEM` | R | `traced` |
| H043 | Product JSON | `POST /workspaces/:id/memories` | `memory.Create` | `MEM` | W | `traced`; projection 동반 |
| H044 | Product JSON | `GET /workspaces/:id/memories` | `memory.List` | `MEM` | R | `traced` |
| H045 | Product JSON | `POST /workspaces/:id/minsu/query` | `minsu.Query` | `MIN` | R | `traced`; retrieval·LLM 외부 호출 |
| H046 | Product JSON | `POST /invitations/accept` | `workspace.AcceptInvitation` | `WSP` | W | `traced` |
| H047 | Product JSON | `GET /projects/:projectId` | `project.Get` | `PRJ` | R | `traced` |
| H048 | Product JSON | `PATCH /projects/:projectId` | `project.Update` | `PRJ` | W | `traced` |
| H049 | Product JSON | `DELETE /projects/:projectId` | `project.Delete` | `PRJ` | W | `traced`; soft delete |
| H050 | Product JSON | `POST /projects/:projectId/milestones` | `milestone.Create` | `MIL` | W | `traced` |
| H051 | Product JSON | `GET /projects/:projectId/milestones` | `milestone.List` | `MIL` | R | `traced` |
| H052 | Product JSON | `POST /projects/:projectId/tasks` | `task.Create` | `TSK` | W | `traced`; REST 외 MCP·Slack task writer도 함께 전환 |
| H053 | Product JSON | `GET /projects/:projectId/tasks` | `task.List` | `TSK` | R | `traced`; 모바일 보드 계약과 별도 |
| H054 | Product JSON | `POST /projects/:projectId/decisions` | `decision.Create` | `DEC` | W | `traced`; projection 동반 |
| H055 | Product JSON | `GET /projects/:projectId/decisions` | `decision.List` | `DEC` | R | `traced` |
| H056 | Product JSON | `GET /milestones/:milestoneId` | `milestone.Get` | `MIL` | R | `traced` |
| H057 | Product JSON | `PATCH /milestones/:milestoneId` | `milestone.Update` | `MIL` | W | `traced` |
| H058 | Product JSON | `DELETE /milestones/:milestoneId` | `milestone.Delete` | `MIL` | W | `traced`; soft delete |
| H059 | Product JSON | `POST /milestones/:milestoneId/blockers` | `blocker.CreateForMilestone` | `BLK` | W | `traced`; projection 동반 |
| H060 | Product JSON | `GET /tasks/:taskId` | `task.Get` | `TSK` | R | `traced`; 모바일 상세 계약과 별도 |
| H061 | Product JSON | `PATCH /tasks/:taskId` | `task.Update` | `TSK` | W | `traced`; projection 동반 |
| H062 | Product JSON | `DELETE /tasks/:taskId` | `task.Delete` | `TSK` | W | `traced`; soft delete·projection 동반 |
| H063 | Product JSON | `GET /tasks/:taskId/updates` | `task.ListUpdates` | `TSK` | R | `traced` |
| H064 | Product JSON | `POST /tasks/:taskId/updates` | `task.CreateUpdate` | `TSK` | W | `traced` |
| H065 | Product JSON | `DELETE /tasks/:taskId/updates/:updateId` | `task.DeleteUpdate` | `TSK` | W | `traced`; soft delete |
| H066 | Product JSON | `POST /tasks/:taskId/blockers` | `blocker.CreateForTask` | `BLK` | W | `traced`; projection 동반 |
| H067 | Product JSON | `POST /tasks/:taskId/memories/:memoryId` | `relation.LinkTaskMemory` | `CTX` | W | `traced` |
| H068 | Product JSON | `DELETE /tasks/:taskId/memories/:memoryId` | `relation.UnlinkTaskMemory` | `CTX` | W | `traced`; soft delete relation |
| H069 | Product JSON | `POST /tasks/:taskId/source-refs` | `relation.CreateTaskSourceRef` | `CTX` | W | `traced`; source-ref 생성과 relation 연결 |
| H070 | Product JSON | `POST /tasks/:taskId/source-refs/:sourceRefId` | `relation.LinkTaskSourceRef` | `CTX` | W | `traced` |
| H071 | Product JSON | `DELETE /tasks/:taskId/source-refs/:sourceRefId` | `relation.UnlinkTaskSourceRef` | `CTX` | W | `traced`; soft delete relation |
| H072 | Product JSON | `GET /tasks/:taskId/context` | `relation.TaskContext` | `CTX` | R | `traced`; memory·source-ref hydrate |
| H073 | Product JSON | `GET /decisions/:decisionId` | `decision.Get` | `DEC` | R | `traced` |
| H074 | Product JSON | `PATCH /blockers/:blockerId/resolve` | `blocker.Resolve` | `BLK` | W | `traced`; projection 동반 |
| H075 | Product JSON | `DELETE /blockers/:blockerId` | `blocker.Delete` | `BLK` | W | `traced`; hard delete 여부 characterization 필요 |
| H076 | Product JSON | `GET /source-connections/:id` | `source.Get` | `SRC` | R | `traced` |
| H077 | Product JSON | `PATCH /source-connections/:id` | `source.Update` | `SRC` | W | `traced` |
| H078 | Product JSON | `POST /source-connections/:id/disable` | `source.Disable` | `SRC` | W | `traced`; worker ingest 중지 계약 확인 |
| H079 | Product JSON | `POST /source-connections/:id/resync` | `source.Resync` | `SRC` | W | `traced`; worker가 관측하는 sync state 변경 |
| H080 | Product JSON | `POST /source-connections/:id/figma/configure` | `source.ConfigureFigma` | `SRC` | W | `traced`; Figma webhook 외부 호출 |
| H081 | Product JSON | `GET /source-connections/:id/sync-states` | `source.ListSyncStates` | `SRC` | R | `traced` |
| H082 | provider callback | `GET /source-connections/oauth/callback` | `source.OAuthCallback` | `SRC` | W | `traced`; public callback, signed state·redirect URI 계약 |
| H083 | Product JSON | `GET /memory-candidates/:id` | `candidate.Get` | `MEM` | R | `traced` |
| H084 | Product JSON | `POST /memory-candidates/:id/confirm` | `candidate.Confirm` | `MEM` | W | `traced`; confirmed memory·review action·projection |
| H085 | Product JSON | `POST /memory-candidates/:id/reject` | `candidate.Reject` | `MEM` | W | `traced`; review action |
| H086 | Product JSON | `POST /memory-candidates/:id/merge` | `candidate.Merge` | `MEM` | W | `traced`; 기존 memory 변경·projection |
| H087 | Product JSON | `POST /memory-candidates/:id/expire` | `candidate.Expire` | `MEM` | W | `traced` |
| H088 | Product JSON | `POST /memory-candidates/:id/edit-and-confirm` | `candidate.EditAndConfirm` | `MEM` | W | `traced`; confirmed memory·review action·projection |
| H089 | Product JSON | `GET /memories/:id` | `memory.Get` | `MEM` | R | `traced` |
| H090 | Product JSON | `PATCH /memories/:id` | `memory.Update` | `MEM` | W | `traced`; projection 동반 |
| H091 | Product JSON | `POST /memories/:id/invalidate` | `memory.Invalidate` | `MEM` | W | `traced`; projection 동반 |
| H092 | Product JSON | `POST /memories/:id/archive` | `memory.Archive` | `MEM` | W | `traced`; projection 동반 |
| H093 | Product JSON | `POST /memories/:id/resolve` | `memory.Resolve` | `MEM` | W | `traced`; projection 동반 |
| H094 | Product JSON | `DELETE /memories/:id` | `memory.Delete` | `MEM` | W | `traced`; soft delete·projection 동반 |
| H095 | Product JSON | `GET /memories/:id/linked-tasks` | `relation.LinkedTasks` | `CTX` | R | `traced` |
| H096 | Product JSON | `POST /source-refs/:id/verify` | `source.VerifySourceRef` | `SRC` | W | `traced` |

## 비-HTTP·도구 진입점 원장

| ID | surface | legacy entry point·trace | profile/target | writer·dependency | status·gate |
| --- | --- | --- | --- | --- | --- |
| N001 | startup migration | `bootstrap.New` → `db.RunMigrations` → `migrations/*.sql` | infra; local/test는 각 target module Flyway, prod DDL owner는 별도 결정 | schema writer, advisory lock | `traced`; prod는 현재 legacy owner 유지, 최종 DDL 소유권 미결 |
| N002 | startup backfill | `bootstrap.New` goroutine → `retrieval.BackfillSearchTokens` | `retrieval`/worker 경계 | `retrieval_documents.search_tokens` writer | `traced`; worker projection·backfill owner와 재처리 계약 필요 |
| N003 | background loop | `bootstrap.New` goroutine → `retrieval.Embedder.Run` ticker | `retrieval`/worker 경계 | retrieval document embedding writer, Vertex AI | `traced`; 중복 실행·비용·지연 관측과 owner 결정 필요 |
| N004 | webhook child runtime | `slackbot.Handler.dispatch` → goroutine `answerAndPost` | `SLK`/`minsu` | Slack API·retrieval·Vertex, action이면 task write | `traced`; H013과 같은 전환 단위 |
| N005 | server lifecycle | `cmd/api/main.go`의 `ListenAndServe` goroutine, signal drain, `App.Close` | `app` | HTTP accept, retrieval client, DB pool lifecycle | `traced`; Spring lifecycle와 k8s probe·termination grace로 대체 |
| N006 | offline CLI | `cmd/seed-demo` → `demo.Run` | 신규 runtime으로 이관하지 않음 | local/demo DB seed, 선택적 migration | `traced`; 유지·대체·폐기 명시 필요, prod guard 보존 |
| N007 | offline CLI | `cmd/minsu-ask` → ungrounded LLM answer | 신규 runtime으로 이관하지 않음 | Vertex AI/ADC | `traced`; 개발 검증 도구로 유지할지 결정 |
| N008 | offline CLI | `cmd/minsu-eval` → `eval.Run` → retrieval gRPC | 신규 runtime으로 이관하지 않음 | retrieval·선택적 Vertex embedding | `traced`; 별도 eval 도구 소유 저장소 결정 |
| N009 | MCP tool | `list_projects` → `mcpserver.listProjects` | `MCP` → `project` | read-only | `traced`; H012와 같은 전환 단위 |
| N010 | MCP tool | `list_members` → `mcpserver.listMembers` | `MCP` → `workspace` | read-only | `traced`; H012와 같은 전환 단위 |
| N011 | MCP tool | `list_milestones` → `mcpserver.listMilestones` | `MCP` → `project` | read-only | `traced`; H012와 같은 전환 단위 |
| N012 | MCP tool | `create_milestone` → `mcpserver.createMilestone` | `MCP` → `project` | milestone writer | `traced`; REST milestone writer와 함께 전환 |
| N013 | MCP tool | `update_milestone` → `mcpserver.updateMilestone` | `MCP` → `project` | milestone writer | `traced`; REST milestone writer와 함께 전환 |
| N014 | MCP tool | `delete_milestone` → `mcpserver.deleteMilestone` | `MCP` → `project` | milestone writer | `traced`; REST milestone writer와 함께 전환 |
| N015 | MCP tool | `create_task` → `mcpserver.createTask` | `MCP` → `project` | task writer | `traced`; REST·Slack task writer와 projection을 함께 전환 |
| N016 | MCP tool | `update_task` → `mcpserver.updateTask` | `MCP` → `project` | task writer | `traced`; REST·Slack task writer와 projection을 함께 전환 |
| N017 | MCP tool | `get_task` → `mcpserver.getTask` | `MCP` → `project` | task·updates read | `traced`; H012와 같은 전환 단위 |
| N018 | MCP tool | `list_tasks` → `mcpserver.listTasks` | `MCP` → `project` | read-only | `traced`; H012와 같은 전환 단위 |
| N019 | MCP tool | `create_comment` → `mcpserver.createComment` | `MCP` → `project` | task update writer | `traced`; REST task update writer와 함께 전환 |

## 첫 수직 슬라이스 후보 비교

첫 슬라이스는 아직 확정하지 않는다. 아래는 원장에서 확인한 read-only 후보이며, write 전환과
projection 공통 기반을 처음부터 묶지 않아도 되는 범위만 비교했다.

| 후보 | 포함 entry | 장점 | 먼저 잠글 계약·제약 | 판단 |
| --- | --- | --- | --- | --- |
| 워크스페이스 목록·상세 | H020, H022 | 사용자 진입 가치가 높고 target `workspace` entity/repository가 이미 있으며 projection 없음 | `MOM-0845`의 모바일 workspace scope, legacy wrapper·403/404·soft-delete characterization | **우선 후보**. MOM-0845 결정 후 가장 작은 read slice로 재평가 |
| 프로젝트 목록·상세 | H038, H047 | target `ProjectReader`와 project backing이 이미 있고 projection 없음 | legacy `health_status`, count, metadata, label, owners와 계산 progress의 응답 정책; `MOM-0845` | 두 번째 후보. legacy field gap이 workspace보다 큼 |
| 마일스톤 목록·상세 | H051, H056 | read-only이고 외부 provider·projection 없음 | target milestone entity/API가 아직 없고 owner·health·progress 전체 mapping 필요 | 독립성은 높지만 첫 slice의 신규 코드량이 더 큼 |
| 태스크 목록·상세 | H053, H060 | 사용자 가치가 높고 target task backing·모바일 read가 존재 | `MOM-0773`, legacy milestone/due date·role/default·progress, 웹/모바일 DTO 분리 | 계약 선행 결정 전에는 첫 slice로 선택하지 않음 |
| 결정·블로커 read | H039, H055, H073 | write를 제외하면 projection 전환 없이 routing rollback 가능 | target entity/API 미구현, legacy 전용 test 없음, 현재 클라이언트 사용 근거 확인 필요 | characterization 근거가 약해 후순위 |

우선 후보는 구현 확정이 아니다. `MOM-0845` 결과와 현재 웹 호출 로그·FE 사용 경로를 확인한 뒤
별도 Momens 작업에서 `migrate-slice`로 route → handler → service → repository → schema → test를
다시 고정한다.

## 미결정 사항

구현 중 조용히 정하지 않는다.

1. 웹 트래픽을 capability별로 혼합 전환할지, 신규 인증과 준비된 Product API를 한 번에 전환할지
2. MCP transport·OAuth authorization server의 target Gradle module과 grant/token 이전 방식
3. `momens-worker`의 공통 outbox 소비 기반(offset·멱등·재시도·DLQ)과 task/decision/blocker/memory
   projector 분리
4. startup retrieval backfill·embedding의 최종 owner와 기존 document 재projection 방식
5. snapshot 합성 endpoint의 target 표면 소유자
6. offline CLI 3개의 유지·대체·폐기와 소유 저장소
7. `MOM-0773` task 계약, `MOM-0774` source-ref 관계, `MOM-0845` workspace scope
8. Product JSON별 실제 웹 사용 여부와 컷오버 관측 기간

## 후속 작업 제안

중복 여부를 Momens에서 다시 확인한 뒤 필요한 것만 만든다.

1. `[Docs] 첫 웹 read 수직 슬라이스 선정과 계약 잠금`
   - H020/H022와 H038/H047을 실제 FE 사용 경로·MOM-0845 결과로 비교
2. `[Docs] MCP/OAuth target module·token/grant 전환 ADR`
   - H002~H012, H035~H036, N009~N019 소유
3. `[Feat] worker outbox 공통 소비 기반`
   - offset, idempotency, retry, DLQ와 관측성만 소유
4. aggregate별 projection 작업
   - task, decision, blocker, memory 이벤트 계약·hydrate·projector를 각각 분리
5. `[Docs] 웹 인증·Product API 컷오버 단위와 rollback runbook`
   - 혼합 트래픽 필요 여부를 먼저 판단하고 필요할 때만 세션 공존 ADR 작성
6. `[Docs] 레거시 offline CLI disposition 결정`
   - N006~N008 유지·이동·폐기와 실행 주체 확정

기존 작업은 새로 만들지 않는다. `MOM-0773`, `MOM-0774`, `MOM-0840`, `MOM-0845`를 해당 gate에서
참조한다.
