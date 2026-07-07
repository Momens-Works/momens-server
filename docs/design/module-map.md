# 모듈 맵 (Module Map)

`momens-server`의 **확정된 도메인 모듈 목록과 책임 경계**를 정의하는 단일 출처입니다.

- 모듈을 *어떻게* 나누는지(상시 규칙)는 [아키텍처](../rules/architecture.md)에, *왜* 그렇게
  정했는지는 [ADR-0001](../adr/0001-modular-monolith-rules.md)·[ADR-0002](../adr/0002-gradle-multi-module-boundaries.md)에
  있습니다. 이 문서는 *무엇이 있는지*(현재 사실)를 담습니다.
- 물리 경계는 Gradle 서브프로젝트, 패키지는 `works.momens.server.*`, 모듈 간 경계는
  Spring Modulith로 검증합니다.
- 모듈은 기능이 추가되며 점진적으로 늘립니다. 새 모듈·경계 변경 시 이 문서를 갱신합니다.

## 모듈 개요

| 모듈 | 책임 | 레거시 흡수 |
| --- | --- | --- |
| `app` | 실행·조립, 전체 컨텍스트 테스트, Modulith 경계 검증 | `bootstrap` |
| `common` | 영속성 베이스·공유 확장·테스트 fixture (최소) | — |
| `user` | 사용자 엔티티·프로필·`/me`, FindOrCreate public API | `domain.User` |
| `auth` | OAuth 로그인·JWT·SecurityFilterChain·logout | `auth` |
| `workspace` | workspace·멤버·초대·RBAC·label 발급 (중심 모듈) | `workspace`·`access`·`label` |
| `project` | project·milestone·task·decision·blocker 운영 흐름 | 동명 5개 패키지 |
| `signal` | 모바일 Signal 원본 조회·사용자 action ledger·Signal action outbox | 신규 |
| `mobile` | 모바일 진입 API. 도메인 public API 조합(얇은 orchestration) | 없음 (신규 표면) |
| `memory` | 메모리 후보 검토·confirmed memory lifecycle | `memory` |
| `source` | 외부 연결 lifecycle·provider OAuth·source-ref verify | `source` |
| `context` | task-memory/source-ref 연결·context API (얇은 orchestration) | `relation` |
| `retrieval` | 검색 read-model projection·document/event schema 소유 | `retrieval` |
| `minsu` | Minsu 질의 usecase·LLM·gRPC client·Slack 표면 | `minsu`·`slackbot` |

## 의존 방향

협력 기본은 application event, 단순한 경우 상대 모듈의 public API 직접 참조입니다([아키텍처 > 모듈 간 의존](../rules/architecture.md)).

- `auth` → `user` public API (로그인 시 FindOrCreate·프로필).
- `workspace`는 RBAC·label을 public API로 제공하고 `project`·`memory`·`source`·`minsu`가 사용한다.
- `context`는 `project`·`memory`·`source`의 public API와 식별자를 조합하는 얇은 capability다.
- `mobile`은 `user`, `project`, `workspace`의 public API만 조합한다(bootstrap, 멤버 조회).
  도메인 정책을 소유하지 않는다.
- `signal`은 `project`의 project/workspace 해석 public API와 `workspace`의 RBAC public API를 사용한다.
  Signal을 task로 수용할 때는 `project`의 task 생성 public API를 사용한다.
- `retrieval`은 `project`·`memory`의 도메인 write 이후 발행(event 또는 public API)을 받는다.
- 다른 모듈의 `internal` package 참조와 순환 의존은 금지한다.

## 모듈별 책임

### app

실행과 조립만 담당한다.

- Spring Boot main, 전체 module wiring
- 전체 application context test
- Spring Modulith boundary verification (`ApplicationModules.of(...).verify()`)

도메인 로직·repository·외부 adapter를 소유하지 않는다.

### common

최소 공통 기반만 담당한다.

- `BaseEntity`, JPA Auditing
- 공유 DB 확장 migration(`uuid-ossp` 등 — [데이터](../rules/persistence.md))
- 공통 테스트 fixture

`common`이 비대해지면 모듈 경계가 흐려지므로 domain helper나 business utility를 넣지 않는다.

### user

사용자 자체와 프로필 속성을 담당한다.

- user entity (email/name/avatar/job role)
- user repository
- `/me` 프로필 조회/수정
- FindOrCreate·프로필 read/update public API (로그인 시 `auth`가 사용)
- 프로필 벌크 조회 public API (`getProfiles`, 멤버 목록처럼 다른 모듈의 userId 목록에 프로필을
  결합할 때 사용. MOM-61)

`user`와 `auth`는 별도 모듈로 둔다. 레거시는 `auth`가 User 영속을 직접 소유했지만, 신규
구조에서는 `user`가 엔티티·프로필·public API를 소유하고 `auth`는 그 public API에 의존한다.
(`identity`로 통합하는 안은 검토했으나 분리 유지로 확정.)

### auth

인증 세션과 로그인 흐름, 보안 인프라를 담당한다.

- Google OAuth login/callback, 외부 신원 검증
- JWT 발급/검증
- SecurityFilterChain, 인증 필터, 공개/보호 엔드포인트 분리
- logout

프로필 조회/수정(`/me`)은 `user`가 소유하고, `auth`는 세션·보안만 책임진다. 인증 세션·전송
모델(모바일 Bearer / 웹 HttpOnly 쿠키 하이브리드, 공통 access+refresh)은 [ADR-0003](../adr/0003-auth-session-transport-model.md),
토큰 발급·검증 스택(Resource Server + JOSE)은 [ADR-0004](../adr/0004-token-issuance-verification-stack.md),
refresh token 저장 모델(서버 저장형 + PostgreSQL 원장)은 [ADR-0005](../adr/0005-refresh-token-storage-model.md)에
기록한다. 웹 쿠키·CSRF 구현은 MOM-22가 맡는다. 이 모듈이 공통 기반 Architecture Spike(MOM-8)에 해당한다.

### workspace

워크스페이스, 멤버십, 초대, RBAC, workspace-scoped 라벨 발급의 중심 모듈이다.

- workspace, workspace member, invitation, onboarding state
- role checks (RBAC)
- label 발급 public API (MEM-0001/TASK-xxxx; `workspace_label_sequences`)

레거시 `access`(중앙 RBAC 서비스)와 `label`(workspace-scoped 라벨 발급)을 이 모듈이 흡수한다.
project나 task가 속한 workspace를 찾는 책임은 해당 리소스를 소유한 모듈이 맡는다. 다른 모듈은
권한 확인·라벨 발급이 필요할 때 `workspace`의 public API를 사용하고, `workspace` 내부
repository를 직접 참조하지 않는다.

내부는 도메인 하위 경계로 논리 분리했다(MOM-70).

- 멤버십(`access`)과 라벨 발급(`label`)은 Spring Modulith nested 논리 모듈이고, 워크스페이스
  코어는 `internal`에 둔다. 공개 계약은 모듈 root의 public API 그대로다.
- 하위 도메인마다 aggregate가 하나씩이고(`WorkspaceMember`, `WorkspaceLabelSequence`,
  `Workspace`) 트랜잭션은 자기 aggregate 안에 닫힌다. 예외는 라벨 발급 한 곳으로, 발급이
  단일 문장(UPSERT)으로 호출자 트랜잭션에 참여한다(MANDATORY). 라벨이 INSERT되는 행에 동기
  반환값으로 들어가고 실패 시 번호가 함께 되돌아가야 해서이고, 레거시 `BEFORE INSERT` 트리거와
  같은 시맨틱을 유지하는 의도된 예외다.

### project

프로젝트 운영 흐름을 하나의 capability로 시작한다.

- project, milestone, task, task update, decision, blocker

레거시에는 각각 패키지가 나뉘어 있지만, Spring Gradle 모듈을 처음부터 과분리하지 않는다.
이들은 프로젝트 실행/운영 맥락 안에서 함께 움직이고, task/decision/blocker는 retrieval
projection도 함께 발생한다. 모델 언어와 변경 이유가 분리될 때 하위 모듈 분리를 재검토한다.

모듈은 모바일 read 기반(MOM-59)으로 시작했다. 현재 사실:

- `projects` 테이블은 레거시 초기 형상에 모바일 스냅샷 컬럼(target_date, progress, summary)을
  더한 범위만 만들었다. 제외한 레거시 컬럼(health_status, 카운트 컬럼, metadata, label)과
  `project_owners`는 웹 이관(MOM-35 계열)에서 추가한다.
- 조회 public API는 `ProjectReader`(workspaceIdOf, findSnapshot, listByWorkspaceIds)와
  `ProjectSnapshot`이다. projectId가 속한 workspace를 찾는 책임은 이 모듈이 소유한다.
- 태스크 도메인은 MOM-62에서 시작했다. `tasks` 테이블은 레거시와 호환되는 범위(모바일 보드와
  생성이 쓰는 컬럼)로 시작했고, 상세(MOM-63)가 읽는 레거시 컬럼(description, assignee_id)을
  더했다. 모바일이 안 쓰는 레거시 컬럼(milestone_id, due_date)은 웹 이관에서 추가한다. roles는
  레거시 tasks에 없는 신규 속성이라 부가 테이블 `task_roles`로 둔다. 완료기준은 레거시에 없는
  신규 테이블 `task_checklist_items`이고, Task aggregate 내부 자식이라 별도 repository 없이
  Task를 통해서만 접근한다(prod 공유 스키마 반영은 task_roles처럼 컷오버 때 조율, P12). 조회
  public API는 `TaskReader`(listTasksByStatus, findDetail)와 `BoardTask`, `TaskDetail`이고, 어떤
  상태를 보일지와 표기 매핑은 표면이 정한다. 생성 public API는 `TaskCreator`(create)이고, 생성
  시 workspace의 `LabelAllocator`로 MOM 라벨을 발급한다.
- project 목록 조회는 호출하는 쪽이 멤버십 조회(`WorkspaceAccess.listUserMemberships`)로
  확정한 workspace id 목록을 받아 자기 테이블에서 조회한다. 멤버십을 이 모듈이 다시 읽지
  않아서 호출 쪽 멤버십 스냅샷과 목록 기준이 항상 같다. 접근 범위(멤버십)는 여전히 호출 쪽이
  넘기지만, task 생성이 `LabelAllocator`를 쓰면서 project는 workspace public API에 런타임으로
  의존한다(MOM-62 이전에는 테스트 스코프의 FK 마이그레이션 의존만 있었다).
- project CRUD API는 아직 없다(MOM-35).

내부는 도메인 하위 경계로 논리 분리했다(MOM-71).

- 태스크(`task`)는 Spring Modulith nested 논리 모듈이고, 프로젝트 코어는 `internal`에 둔다.
  공개 계약은 모듈 root의 public API 그대로다.
- 하위 도메인마다 aggregate가 하나씩이고(`Project`, `Task`) 트랜잭션은 자기 aggregate 안에
  닫힌다. 예외는 태스크 생성 트랜잭션에 참여하는 라벨 발급 한 곳이고, 위 workspace 절에
  문서화했다. `Task`는 `Project`를 엔티티 연관이 아니라 projectId로만 참조한다.

### mobile

모바일 앱 진입 API를 담당하는 얇은 orchestration 모듈이다([아키텍처 > 모듈 경계](../rules/architecture.md)).

- `GET /api/mobile/bootstrap`: 내 정보(user), 접근 가능한 project 목록(project), 멤버십
  role 매핑(workspace)을 조합해 진입 컨텍스트를 내린다(MOM-60).
- `GET /api/mobile/projects/{projectId}/members`: project의 workspace 해석(project), 멤버십
  스냅샷(workspace), 프로필 결합(user)을 조합해 담당자 선택용 멤버 목록을 내린다. 검색과
  정렬은 조합 규칙이라 이 모듈이 소유한다(MOM-61).
- `GET`과 `POST /api/mobile/projects/{projectId}/tasks`: project의 태스크 도메인(보드 조회, 생성)과
  workspace 멤버십을 조합한다. 보드에 노출할 상태와 순서, 라벨은 `BoardStatus` enum 한곳에 모아
  이 모듈이 소유하고(backlog와 cancelled 제외), project 조회에는 그 상태 목록을 넘긴다. priority
  매핑(urgent를 high로 반환), material_count 기본값도 조합 규칙이라 이 모듈이 소유한다(MOM-62).
- `GET /api/mobile/tasks/{taskId}`: project의 태스크 상세(`TaskReader.findDetail`)와 workspace
  멤버십(태스크가 속한 workspace 기준), user 프로필(담당자 이름)을 조합한다. purpose 개명
  (도메인 description), priority 매핑, 빈 값 고정(materials와 open_questions는 빈 배열,
  next_action은 null)은 조합 규칙이라 이 모듈이 소유한다(MOM-63). 수정 계열은 MOM-75가 같은
  `/tasks/*` 표면에 추가한다.
- 도메인 정책과 영속성을 소유하지 않는다. 엔티티, repository, 마이그레이션이 없다.
- 어느 한 도메인의 capability가 아닌 모바일 조합 표면(진입처럼 여러 모듈을 가로지르는 조회)이
  이 모듈에 온다. 모바일 API 전부를 모으는 곳은 아니며, 도메인 스코프가 분명한 모바일 API는
  해당 도메인 모듈이 소유한다. Signal API는 `signal` 모듈이 소유한다.

### signal

모바일 Signal 원본과 사용자 처리 흐름을 담당한다.

- `signals` 조회 모델: worker가 생성한 Signal 원본을 프로젝트 스코프로 조회한다.
- `signal_evidence`: Signal과 `source_refs`의 근거 연결을 읽어 모바일 상세 응답을 조립한다.
- `signal_actions`: 사용자의 `convert-to-task`, `dismiss` 처리 기록과 멱등성을 소유한다.
- Signal 목록/상세 및 action API를 소유한다. 경로가 `/api/mobile/*`여도 Signal 도메인 정책과
  영속성은 `mobile`이 아니라 이 모듈에 둔다.
- Signal action 결과 outbox 발행 계약을 소유한다. projection 경로의 outbox 소비 상태, 재시도, DLQ는
  worker 책임이고, retrieval indexing 상태는 retrieval 책임이다.
- Signal 발생 push notification은 api-server가 worker의 `signal.created` outbox를 소비해 발송한다
  ([ADR-0009](../adr/0009-notification-consumer-ownership.md)). 이 소비/발송을 `signal` 모듈이 소유할지
  별도 notification 관심사로 둘지는 **미결정**이며 구현 PR에서 정한다.

신규 Signal backing은 `memory_candidates`와 분리한다. 이유와 결과는
[ADR-0007](../adr/0007-signal-backing-and-module-boundary.md)에 기록한다.

### memory

제품 기억 lifecycle을 담당한다.

- memory candidate review, confirmed memory
- review actions, manual memory create, status transition

`source`에서 생성한 후보를 사람이 검토하고 확정하는 API 책임은 `memory`가 가진다.

### source

외부 출처 연결 상태와 source reference를 담당한다.

- source connection lifecycle, source credentials custody
- provider install/callback, sync states
- source refs verify

외부 데이터 ingest·curation은 `momens-worker` 책임이다. `source` 모듈은 API 서버가 소유하는
연결/토큰/참조 검증 계약만 담당한다.

### context

엔티티 간 연결과 context bundle을 담당한다.

- task-memory link, task-source-ref link
- task context API, memory linked-tasks reverse lookup
- entity_relations

`project`·`memory`·`source`를 가로지르는 얇은 연결 capability다. 도메인 정책을 많이 소유하기보다
각 모듈 public API와 식별자를 조합한다.

### retrieval

검색 read-model publication을 담당한다.

- projection writer, retrieval document/event schema ownership
- document id convention, tokenization/backfill/embedding metadata (API 소유분)
- domain write 이후 read-model 발행 API

`task`·`decision`·`blocker`·`memory`는 `retrieval_documents` 세부를 직접 알지 않고 `retrieval`의
public API 또는 application event로 발행한다.

### minsu

Minsu query experience를 담당한다.

- `/workspaces/:id/minsu/query`
- retrieval SearchRequest assembly, PermissionContext assembly, answer synthesis
- Slack bot 표면, LLM adapter, retrieval gRPC client adapter

Minsu는 검색을 호출하고 답변을 만드는 유스케이스를 소유한다. retrieval projection schema
ownership과는 분리한다. 레거시 `slackbot`의 표면(Slack 이벤트 처리)도 이 모듈이 흡수한다.

## 레거시 매핑 요약

| 신규 모듈 | 흡수하는 레거시 패키지 |
| --- | --- |
| `user` | `auth`의 User 영속, `domain.User` |
| `auth` | `auth`의 세션·로그인·보안 |
| `workspace` | `workspace`, `access`(RBAC), `label` |
| `project` | `project`, `milestone`, `task`, `decision`, `blocker` |
| `signal` | 신규 |
| `memory` | `memory` |
| `source` | `source` |
| `context` | `relation` |
| `retrieval` | `retrieval` |
| `minsu` | `minsu`, `slackbot` |

**신규 모듈로 옮기지 않는 레거시 패키지 (런타임 API 아님):**

- `eval` — 오프라인 검색 평가 하네스
- `demo` — 로컬 데모 시드
- `platform` / `config` / `domain` / `bootstrap` / `testdb` — Spring 기본 인프라로 대체

## 열린 항목

- **`project` 하위 분리** (task/decision/blocker 별도 모듈화): 모델 언어와 변경 이유가
  분리될 때 재검토.
