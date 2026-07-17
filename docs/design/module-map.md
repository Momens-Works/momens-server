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
| `outbox` | append-only outbox 발행 로그 공용 모듈 (ADR-0008) | 신규 |
| `notification` | Signal 발생 push notification 소비·발송, push 설치(FID/FCM token) lifecycle | 신규 |
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
- `context`는 `entity_relations`를 읽어 연결된 식별자만 돌려준다. 지금은 도메인 모듈에 의존하지 않고,
  식별자로 본문을 채우는 조합은 소비하는 쪽이 한다(`mobile`이 `context`의 링크와 `source`의
  source_ref 조회를 엮어 태스크 관련자료를 만든다).
- `mobile`은 `user`, `project`, `workspace`, `signal`, `context`, `source`, `notification`의 public
  API만 조합한다(bootstrap, 멤버 조회, 브리프, 태스크 관련자료, push 설치 등록·해제). 도메인 정책을
  소유하지 않는다.
- `signal`은 `project`의 project/workspace 해석 public API와 `workspace`의 RBAC public API를 사용한다.
  상세 응답의 evidence는 `source`의 source_ref 조회 public API로 hydrate한다.
  Signal을 task로 수용할 때는 `project`의 task 생성 public API를 사용한다.
- `signal`과 `project`는 확정 액션 결과를 같은 트랜잭션에서 남기기 위해 `outbox`의
  `OutboxAppender` public API를 사용한다(CO-6). `signal`의 dev Signal 생성 쓰기 경로는 `source`의
  dev 쓰기 public API(`DevSourceRefWriter`)에도 위임한다.
- `notification`은 `outbox`의 조회 public API(`OutboxEventReader`)로 `signal.created`를 소비하고,
  `signal`의 `SignalReader`로 Signal을 hydrate하며, `project`의 프로젝트명 조회와 `workspace`의
  `WorkspaceAccess.listMemberships`로 수신자를 결정한다. `outbox`는 다른 도메인 모듈을 참조하지 않는다.
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
- dev 도구 전용 빈 프로필 게이트 애너테이션 `@DevOnly`(`works.momens.server.common.config`). `auth`의
  dev 토큰 발급, `signal`·`source`의 dev Signal 생성 쓰기 경로가 공유해서 쓴다(MOM-0690).

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
- dev 전용 토큰 발급 엔드포인트(`POST /api/auth/dev/token`, MOM-90). dev 계열 프로필(`@DevOnly`)에서만 등록되고 공유 시크릿 헤더와 테스트 사용자 allowlist로 제한한다. prod에는 존재하지 않는다.

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
  더했다. 모바일이 안 쓰는 레거시 컬럼(milestone_id, due_date)은 웹 이관에서 추가한다. role은
  레거시 tasks에 없는 신규 속성이지만 태스크당 하나만 선택하는 단일 값이라(MOM-76, 2026-07-07
  확정) priority와 같은 방식으로 CHECK 제약을 둔 문자열 컬럼에 저장한다. 완료기준은 레거시에
  없는 신규 테이블 `task_checklist_items`이고, Task aggregate 내부 자식이라 별도 repository
  없이 Task를 통해서만 접근한다(prod 공유 스키마 반영은 컷오버 때 조율). 민수 산출물인 열린질문
  (`task_open_questions`)과 다음행동(`tasks.next_action`)도 이 모듈이 소유하지만, 완료기준과 달리
  이 서버에 쓰기 경로가 없어 읽기 전용으로 매핑한다(MOM-0788). 민수 구현 전에는 같은 backing
  계약을 따르는 fixture가 채운다(ADR-0011). 조회 public API는
  `TaskReader`(listTasksByStatus, findDetail)와 `BoardTask`, `TaskDetail`이고, 어떤 상태를
  보일지와 표기 매핑은 표면이 정한다. `BoardTask`는 표면이 생성 시각 기준으로 다시 정렬할 수
  있게 createdAt을 포함한다(MOM-67). 생성 public API는 `TaskCreator`(create)이고, 생성 시
  workspace의 `LabelAllocator`로 MOM 라벨을 발급한다.
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

### outbox

append-only outbox 발행 로그 공용 모듈이다(ADR-0008).

- `outbox_events` 엔티티·리포지토리·마이그레이션을 소유한다.
- 쓰기 public API `OutboxAppender`: 호출자 트랜잭션에 합류해(`Propagation.MANDATORY`)
  `{workspace_id, aggregate_type, aggregate_id, event_type, payload}`를 append한다. 멱등키
  (`"{event_type}:{aggregate_id}"`)는 이 모듈이 결정적으로 조립하고, `idempotency_key UNIQUE` +
  `ON CONFLICT DO NOTHING`으로 dedup한다(SD-3).
- 조회 public API `OutboxEventReader`(`readAfter`, `latestIdBefore`, `findById`)와
  `OutboxEventView`(docs/design/signal-push-demo-design.md 10절). watermark 이후 event를 id
  오름차순으로 읽되 DB 시계 기준 안전 지연을 지나지 않은 첫 event에서 멈춘다(prefix-cap).
  watermark 등 소비 상태 관리는 이 모듈이 아니라 consumer가 소유한다.
- `issued_by`는 이 서버가 발행하는 이벤트만 다루므로 `api-server`로 고정한다. worker가 발행하는
  `signal.created`는 worker 쪽 책임이라 이 모듈이 쓰지 않는다.
- 다른 도메인 모듈을 참조하지 않는다. `signal`과 `project`가 쓰기 public API를, `notification`이
  조회 public API를 사용한다.
- outbox 소비(consumer)의 상태 관리·재시도·DLQ는 이 모듈의 책임이 아니다. worker의 projection 소비는
  ADR-0008, api-server의 `signal.created` notification 소비(`notification` 모듈)는 ADR-0009를
  따르며 둘 다 별도 구현이다.

### notification

Signal 발생 push notification의 소비·발송과 push 설치(FID/FCM token) lifecycle을 담당한다
([설계](signal-push-demo-design.md) 7·8·10·11절, [ADR-0009](../adr/0009-notification-consumer-ownership.md)).

- `push_installations`, `push_deliveries`, `notification_consumer_offsets` 테이블과 Flyway
  마이그레이션을 소유한다.
- `signal.created` outbox consumer: watermark를 관리하며 1초 주기로 폴링하고, id 순서로 읽다가 DB
  시계 기준 안전 지연(2초)을 지나지 않은 첫 event에서 멈춘다(prefix-cap). 최초 기동 시 watermark도
  같은 안전 prefix의 끝으로 시드한다.
- FCM 발송기: `FOR UPDATE SKIP LOCKED`로 delivery를 클레임하고 DB 시계 기준 30초 처리 lease로
  다중 인스턴스의 동시 재클레임을 막는다. 일시 실패 결과를 기록하면 `1초 → 5초 → 30초` 백오프로
  전환해 최초 전송 포함 최대 4회 시도한다. 무효·만료 token은 재시도 없이 installation을
  비활성화한다. event 단위로 Signal·Project를 hydrate해 문구를 만들고, FCM multicast 요청은 최대
  500 token 단위로 분할한다.
- Firebase Admin SDK adapter: Application Default Credentials로 초기화한다.
- public API는 설치 등록·해제 하나뿐이다 — `PushDeviceRegistrar`. consumer와 발송기는 다른 모듈에
  공개할 계약이 없다.
- 폴링과 발송은 `momens.notification.push.enabled` 프로퍼티로 게이트한다(기본 `false`, dev
  `true`).

내부는 변경 이유가 다른 하위 도메인 경계로 논리 분리한다(MOM-0690). `device`(설치 원장, aggregate
`PushInstallation`), `consume`(outbox 소비 진행 위치와 폴링, aggregate `NotificationConsumerOffset`),
`dispatch`(기기별 발송 상태와 배달 실행, aggregate `PushDelivery`), `fcm`(외부 Firebase adapter)은 각각
Spring Modulith nested 논리 모듈이다. nested 간 협력은 단방향 계약으로만 한다: `consume →
dispatch`(`PushDispatcher`: 수신 설치별 발송 기록 enqueue와 발송 패스 트리거),
`consume`·`dispatch → device`(`PushInstallationDirectory`: 수신자 조회·전송 직전 재확인·무효 token
비활성화), `dispatch → fcm`(`FcmClient`·`PushMessage`). 소비 replay의 중복 발송 방지(복합 PK 멱등
기록)는 dispatch가 소유하고, Firebase SDK 타입은 `fcm` 밖으로 새지 않는다.
- 의존 방향: `mobile`이 기기 등록·해제 HTTP 표면을 이 모듈의 public API에 위임하고, 이 모듈은
  `outbox`(`OutboxEventReader`), `signal`(`SignalReader` hydrate), `project`(프로젝트명 조회),
  `workspace`(`WorkspaceAccess.listMemberships` 수신자 결정)의 public API를 사용한다.

### mobile

모바일 앱 진입 API를 담당하는 얇은 orchestration 모듈이다([아키텍처 > 모듈 경계](../rules/architecture.md)).

- `GET /api/mobile/bootstrap`: 내 정보(user), 접근 가능한 project 목록(project), 멤버십
  role 매핑(workspace)을 조합해 진입 컨텍스트를 내린다(MOM-60).
- `GET /api/mobile/projects/{projectId}/members`: project의 workspace 해석(project), 멤버십
  스냅샷(workspace), 프로필 결합(user)을 조합해 담당자 선택용 멤버 목록을 내린다. 검색과
  정렬은 조합 규칙이라 이 모듈이 소유한다(MOM-61).
- `GET`과 `POST /api/mobile/projects/{projectId}/tasks`: project의 태스크 도메인(보드 조회, 생성)과
  workspace 멤버십을 조합한다. 보드에 노출할 상태와 순서, 라벨은 `BoardStatus` enum 한곳에 모아
  이 모듈이 소유하고(저장 상태 5종 전부. MOM-75에서 backlog와 cancelled를 추가), project 조회에는
  그 상태 목록을 넘긴다. priority 매핑(urgent를 high로 반환), material_count 기본값도 조합
  규칙이라 이 모듈이 소유한다(MOM-62).
- `GET /api/mobile/projects/{projectId}/brief`와 `GET .../brief/signal-summary`: project의
  스냅샷(`ProjectReader.findSnapshot`)과 태스크(`TaskReader.listTasksByStatus`), signal의
  당일 시그널 요약(타입별 개수와 커서 페이지, `SignalListService.countByCreatedRange`와
  `listByCreatedRange`), workspace 멤버십을 조합해 브리프 화면 정보를 내린다. project 조회
  결과에 workspace id가 포함되어 있어 workspace를 따로 조회하지 않고 바로 멤버십을 검사한다.
  브리프는 오늘의 브리프라 당일 생성된 시그널을 처리 여부와 무관하게 집계한다(MOM-81). 하루
  경계를 어떤 타임존으로 볼지(Asia/Seoul 고정)는 `BriefDay`가 소유하고, 시각은 `mobileClock`
  으로 주입한다. 시그널 요약 필터 칩(당일 시그널의 type으로 데이터 기반 구성, 라벨은 type의 첫 글자만
  대문자로 바꾸고, All을 맨 앞에 둔 뒤 라벨 글자수와 알파벳순 정렬), 페이지
  기본 크기 3, 현재 우선순위 구성(진행 중인 todo와 in_progress만 후보, priority 높은 순과 생성
  오래된 순 정렬, 상위 4개)은 조합 규칙이라 `SignalTypeLabel`과 `MobilePriority`, 조합 서비스가
  소유한다(MOM-67).
- `GET /api/mobile/tasks/{taskId}`: project의 태스크 상세(`TaskReader.findDetail`)와 workspace
  멤버십(태스크가 속한 workspace 기준), user 프로필(담당자 이름)을 조합한다. purpose 개명
  (도메인 description), priority 매핑, 빈 값 고정(open_questions는 빈 배열, next_action은 null)은
  조합 규칙이라 이 모듈이 소유한다(MOM-63). 관련자료는 context의 링크와 source의 원본을 조합해
  채우고, 연결이 없으면 빈 배열이다(MOM-0779). 수정 계열은 MOM-75가 같은 `/tasks/*` 표면에
  추가한다.
- `GET /api/mobile/projects/{projectId}/signals`, `GET /api/mobile/signals/{signalId}`,
  `POST /api/mobile/signals/{signalId}/actions/convert-to-task`,
  `POST /api/mobile/signals/{signalId}/actions/dismiss`: Signal 목록·상세·action 컨트롤러를 소유하고
  signal의 public API(`SignalListService`·`SignalDetailService`·`SignalActionService`)에 위임한다.
  Signal 도메인 정책·영속성은 여전히 `signal` 모듈이 소유한다(`/api/mobile/*` 표면 소유 원칙에 맞춰
  MOM-0799에서 이관, ADR-0007의 backing 분리는 유지).
- 도메인 정책과 영속성을 소유하지 않는다. 엔티티, repository, 마이그레이션이 없다.
- `/api/mobile/*` HTTP 표면은 도메인 스코프가 분명해도 이 모듈이 소유한다. 컨트롤러는 항상 이
  모듈에 두고 해당 도메인의 public API에 위임한다.

내부는 화면(entry point) 단위로 논리 분리한다(MOM-0799). `bootstrap`·`roster`·`board`·`brief`·
`signal`·`pushdevice`는 각각 Spring Modulith nested 논리 모듈이고, `workspace`/`project`/`signal`의 nested
분리(MOM-70·MOM-71·MOM-65)와 달리 aggregate가 아니라 화면 단위 조합 슬라이스다. 다른 모듈에 공개할
계약이 없으므로 각 nested 패키지는 Controller·Docs·조합 서비스·DTO를 한곳에 모은다. 조합 서비스처럼
같은 nested 패키지 안에서만 쓰는 타입은 package-private으로 닫아 두고, `dto` 서브패키지가 참조하는
타입은 Java package-private이 서브패키지까지 뻗지 않아 부득이 public으로 남긴다. 모듈 root에는 두
개 이상의 nested 모듈이 공유하거나 모듈 밖에서 참조해야 하는 계약만 남긴다(`MobileClock`,
`MobilePriority`).

- `bootstrap` — `GET /api/mobile/bootstrap`.
- `roster` — `GET /api/mobile/projects/{projectId}/members`. `workspace`의 멤버십, `user`의 프로필
  결합과 헷갈리지 않도록 `members`가 아닌 `roster`로 이름 붙였다.
- `board` — 태스크 보드·생성(`/api/mobile/projects/{projectId}/tasks`)과 태스크 상세·수정·완료기준
  토글(`/api/mobile/tasks/{taskId}` 계열). `project`가 Task aggregate를 소유하는 nested `task`
  모듈과 이름이 겹치지 않도록 화면 이름을 따 `board`로 붙였다. priority 저장값 해석(`MobilePriority`)은
  `brief`와 공유해 모듈 root에 둔다.
- `brief` — `GET /api/mobile/projects/{projectId}/brief`, `.../brief/signal-summary`.
- `signal` — Signal 목록·상세·action 컨트롤러. 위임 전용이라 조합 서비스가 없다.
- `pushdevice` — `PUT`/`DELETE /api/me/push-devices/{firebaseInstallationId}`. push 설치 lifecycle
  정책·영속성을 소유하는 `notification`의 public API(`PushDeviceRegistrar`)에 위임만 하는 얇은
  표면이라 조합 서비스가 없다.

### signal

모바일 Signal 원본과 사용자 처리 흐름을 담당한다.

- `signals` 조회 모델: worker가 생성한 Signal 원본을 프로젝트 스코프로 조회한다.
- `signal_evidence`: Signal과 `source_refs`의 근거 연결 및 근거별 `대상`·`변화`·`영향`을 읽어 모바일
  상세 응답을 조립한다. 의미 값은 worker 또는 같은 backing 계약의 fixture가 생산한다(ADR-0011).
- `signal_actions`: 사용자의 `convert-to-task`, `dismiss` 처리 기록과 멱등성을 소유한다.
- `signal_digests`: 브리프의 시그널 요약 문단을 읽는다. 그날 신호 전체를 한 문단으로 요약한 민수
  산출물이라 서버는 쓰지 않고, 민수 구현 전에는 fixture가 채운다(ADR-0011). 조회 public API는
  `SignalDigestReader`이고 브리프가 시그널을 거르는 생성 시각 범위를 그대로 받는다. 문단과 그
  문단이 설명하는 시그널이 같은 기준으로 걸러져 어긋날 수 없다(ADR-0012). 시그널 한 건을 뜻하는
  `SignalSummary`와는 다른 값이다.
- Signal 목록/상세 및 action의 도메인 정책과 영속성을 소유하고 public API(`SignalListService`·
  `SignalDetailService`·`SignalActionService`)로 노출한다. HTTP 표면(`/api/mobile/*` 컨트롤러)은
  `mobile`이 소유하고 이 public API에 위임한다(MOM-0799).
- 시그널 탭의 미처리 목록 조회(`listUnprocessed`)와, 브리프가 쓰는 당일 생성 범위의 커서 페이지
  조회(`listByCreatedRange`), 타입별 개수 집계(`countByCreatedRange`)를 `SignalListService`가
  소유한다. 당일 범위 조회는 처리 여부와 무관하게 담고 소프트 삭제는 제외한다(MOM-81). 정렬
  기준과 커서 규칙도 이 모듈이 정하고, 어떤 type을 노출할지와 하루 경계는 호출하는 표면이 정한다.
- body 없는 `convert-to-task`는 태스크 등록 시점에 민수가 생성하는 task draft를 입력으로 사용한다.
  민수(서버 내 모듈로 구현 예정)가 구현되기 전에는 고정 목 draft(title=Signal title, role=`pm`,
  priority=`medium`)를 쓰고, draft는 `signals` backing에 저장하지 않는다(ADR-0011).
- Signal action 결과 outbox 발행 계약을 소유한다. projection 경로의 outbox 소비 상태, 재시도, DLQ는
  worker 책임이고, retrieval indexing 상태는 retrieval 책임이다.
- Signal 발생 push notification은 api-server가 `signal.created` outbox를 소비해 발송한다
  ([ADR-0009](../adr/0009-notification-consumer-ownership.md)). 이 소비/발송은 `signal` 모듈이 아니라
  `notification` 모듈이 소유하는 것으로 확정했다([설계](signal-push-demo-design.md) 11.1절).
- dev 데모용 Signal 생성 API(`POST /api/dev/projects/{projectId}/signals`, `@DevOnly`)와 그 전용
  쓰기 경로는 `dev` nested 모듈이 소유한다([설계](signal-push-demo-design.md) 5·11.2절). `signals`·
  `signal_evidence`는 `@Immutable` 읽기 엔티티를 재사용하지 않는 전용 insert 경로를 쓰고,
  `source_refs` 쓰기는 `source`의 dev 쓰기 public API(`DevSourceRefWriter`)에 위임하며,
  `signal.created` outbox 발행은 같은 트랜잭션에서 수행한다. prod 프로필에는 endpoint 자체가
  등록되지 않는다.

신규 Signal backing은 `memory_candidates`와 분리한다. 이유와 결과는
[ADR-0007](../adr/0007-signal-backing-and-module-boundary.md)에 기록한다.

내부는 도메인 하위 경계로 논리 분리한다(MOM-65). Signal 조회(`query`)와 action(`action`)은 각각
Spring Modulith nested 논리 모듈이고, `project`의 `task`와 같은 방식을 따른다: 공개 계약(조회는
`SignalListService`·`SignalDetailService`·`SignalReader` 인터페이스와 `SignalDetail`·
`SignalSummary`, `SignalSummaryPage`, `SignalSnapshot` 레코드, action은 `SignalActionService`
인터페이스와 `SignalActionResult` 레코드)는 모듈 root에 두고, 구현체(`*Impl`)와
엔티티·리포지토리만 각 nested 패키지 안에 package-private로 은닉한다. nested 모듈이 자기 구현에서
root의 타입(`SignalErrorCode` 등)을 참조하는 것은 단방향(query/action → root)이라 순환이 아니다 —
모듈 root(공개 계약)가 nested 모듈의 구현 타입을 직접 참조하는 반대 방향의 의존이 생겼을 때만
Modulith가 순환으로 판정한다. action은 query가 공개한 `SignalReader`로 Signal 스냅샷을 읽고,
convert-to-task는 `project`의 `TaskCreator.create`/`TaskReader.findDetail`(둘 다 root-to-root,
다른 top-level 모듈 참조라 표준 방향)을 쓴다. convert-to-task 트랜잭션(`tasks insert +
signal_actions insert + outbox_events insert 2건`)과 dismiss 트랜잭션(`signal_actions insert +
outbox_events insert 1건`)은 `SignalActionExecutor`가 소유하고, facade(`SignalActionServiceImpl`)와
빈을 분리해 멱등·충돌 정책과 원자 쓰기를 나눈다. outbox 이벤트 insert는 `outbox` 모듈의
`OutboxAppender`를 호출자 트랜잭션에 합류시켜 남긴다(CO-6).

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
- dev 전용 `source_refs` 쓰기 public API `DevSourceRefWriter`(`@DevOnly`). `signal`의 dev Signal
  생성 쓰기 경로가 호출자 트랜잭션에 합류시켜 사용한다([설계](signal-push-demo-design.md) 11.2절).

외부 데이터 ingest·curation은 `momens-worker` 책임이다. `source` 모듈은 API 서버가 소유하는
연결/토큰/참조 검증 계약만 담당한다.

### context

엔티티 간 연결과 context bundle을 담당한다.

- task-memory link, task-source-ref link
- task context API, memory linked-tasks reverse lookup
- entity_relations

여러 도메인을 가로지르는 얇은 연결 capability다. 도메인 정책을 소유하지 않고 연결 자체만 읽는다.

현재 사실: `EntityRelationReader`가 태스크에 연결된 source_ref 식별자 목록과 개수를 돌려준다
(`from_entity_type='TASK'`, `to_entity_type='SOURCE_OBJECT'`, `relation_type='LINKED_TO'`).
식별자로 본문을 채우는 hydrate는 `source`의 public API로 소비하는 쪽이 한다. `entity_relations`는
레거시가 소유하는 외부 테이블이라 읽기 전용이고, local/test는 미러를 쓴다([데이터](../rules/persistence.md)).

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
| `outbox` | 신규 |
| `notification` | 신규 |
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
