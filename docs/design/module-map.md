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

`user`와 `auth`는 별도 모듈로 둔다. 레거시는 `auth`가 User 영속을 직접 소유했지만, 신규
구조에서는 `user`가 엔티티·프로필·public API를 소유하고 `auth`는 그 public API에 의존한다.
(`identity`로 통합하는 안은 검토했으나 분리 유지로 확정.)

### auth

인증 세션과 로그인 흐름, 보안 인프라를 담당한다.

- Google OAuth login/callback, 외부 신원 검증
- JWT 발급/검증
- SecurityFilterChain, 인증 필터, 공개/보호 엔드포인트 분리
- logout

프로필 조회/수정(`/me`)은 `user`가 소유하고, `auth`는 세션·보안만 책임진다. 인증 상세(토큰
전송 방식·수명 — 모바일+웹 하이브리드 여부 등)는 [P6](../pending-decisions.md)에서 확정한다.
이 모듈이 공통 기반 Architecture Spike(MOM-8)에 해당한다.

### workspace

워크스페이스, 멤버십, 초대, RBAC, workspace-scoped 라벨 발급의 중심 모듈이다.

- workspace, workspace member, invitation, onboarding state
- role checks (RBAC)
- resource workspace resolution public API
- label 발급 public API (MEM-0001/TASK-xxxx; `workspace_label_sequences`)

레거시 `access`(중앙 RBAC 서비스)와 `label`(workspace-scoped 라벨 발급)을 이 모듈이 흡수한다.
다른 모듈은 권한 확인·라벨 발급이 필요할 때 `workspace`의 public API를 사용하고, `workspace`
내부 repository를 직접 참조하지 않는다.

### project

프로젝트 운영 흐름을 하나의 capability로 시작한다.

- project, milestone, task, task update, decision, blocker

레거시에는 각각 패키지가 나뉘어 있지만, Spring Gradle 모듈을 처음부터 과분리하지 않는다.
이들은 프로젝트 실행/운영 맥락 안에서 함께 움직이고, task/decision/blocker는 retrieval
projection도 함께 발생한다. 모델 언어와 변경 이유가 분리될 때 하위 모듈 분리를 재검토한다.

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

- **auth 인증 상세** (토큰 전송 방식·수명, 모바일+웹 하이브리드 여부): [P6](../pending-decisions.md).
- **`project` 하위 분리** (task/decision/blocker 별도 모듈화): 모델 언어와 변경 이유가
  분리될 때 재검토.
