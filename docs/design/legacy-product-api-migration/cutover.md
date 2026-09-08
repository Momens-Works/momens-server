# 웹 컷오버 실행과 rollback runbook

상태: 전환 단위 확정 (1단계 착수 전)

작성일: 2026-09-08

레거시 기준선: `Momens-Works/momens-api@71bbd07614fd2aef4dec726bafdf86c1bd097ba6`

FE 기준선: `momens-fe@d76a2d5`

관련 작업: `MOM-0911`

관련 문서: [이관 전략](strategy.md),
[이관 원장](ledger.md),
[ADR-0016](../../adr/0016-user-identity-key-google-sub.md),
[ADR-0017](../../adr/0017-transitional-legacy-session-token-acceptance.md),
[ADR-0018](../../adr/0018-transitional-legacy-acceptance-of-new-access-token.md)

## 1. 읽는 법

컷오버를 **실행하는 사람**을 위한 문서다. 무엇을 어떤 순서로 뒤집고, 무엇을 보고, 어떻게
되돌리는지만 담는다.

전환 단위를 왜 이렇게 정했는지 — capability별 혼합 전환을 기각한 근거, 2단계로 나눈 근거,
관측을 두 창으로 나눈 근거 — 는 `MOM-0911`에 있다. 결정을 다시 열 때 그쪽을 읽는다.

**확정한 전환 단위는 인증 → Product API 2단계다.** 두 단계는 서로 다른 스위치를 뒤집고 서로
다른 게이트를 가진다.

| | 1단계 인증 | 2단계 Product API |
| --- | --- | --- |
| 뒤집는 스위치 | `VITE_AUTH_LOGIN_URL` | `VITE_API_BASE_URL` + provider redirect URI |
| 움직이는 writer | `users` | 나머지 전 aggregate |
| 선행 게이트 | 3절 | 5절 |
| 롤백 | routing rollback만으로 닫힘 | 데이터 호환성 확인 필요 |
| 착수 | 가능 | 게이트 미해소 |

## 2. 전환 스위치

두 서버는 같은 host `api.momens.works`를 경로 prefix로 나눠 쓴다. ingress-nginx의 최장 prefix
매칭이 `/api/*`를 `momens-server`로, `/`를 `momens-api`로 보낸다
(`k8s/manifests/apps/momens-server/ingress.yaml`). **서버나 라우팅 규칙은 건드리지 않는다.**

| 스위치 | 무엇을 결정하는가 | 위치 |
| --- | --- | --- |
| `VITE_AUTH_LOGIN_URL` | 로그인 진입점. 브라우저 내비게이션이라 API client를 타지 않는다 | `src/api/config.ts:18` |
| `VITE_API_BASE_URL` | `MomensApiClient`의 모든 XHR. endpoint별 분기가 없다 | `src/api/config.ts:9`, `src/api/client.ts:58` |
| source provider redirect URI | 소스 연동 콜백이 어느 서버로 돌아오는가 | GitHub·Slack·Notion·Figma 콘솔 |

Vite가 빌드 타임에 앞의 두 값을 굽는다. **되돌리는 것은 설정 플립이 아니라 재빌드·재배포다.**
롤백 소요 시간이 곧 FE 재배포 시간이다.

두 env의 독립은 조건부다. `VITE_AUTH_LOGIN_URL`이 비면 `baseUrl`에서 파생된다
(`src/api/config.ts:18-20`). `.env.production`이 값을 명시하고 있어서 독립이 성립하므로,
**그 값을 비우거나 지우면 `baseUrl` 전환이 로그인 진입점까지 함께 옮긴다.** 2단계 분할 전체가
이 한 줄에 걸려 있다.

## 3. 1단계 게이트

순서대로 닫는다.

1. **`MOM-0873` 두 서버 JWT 서명 키 동일성 확인.** 확인 대상이 리포에 불변식으로 적혀 있다 —
   `k8s/manifests/apps/momens-server/secret.example.yaml:25-28`의
   *"TRANSITION INVARIANT: keep this EQUAL to momens-api's JWT_SECRET during the dual-run"*.
   값이 다르면 전환 즉시 웹 전체가 401을 받고, 테스트로는 잡히지 않는다.
2. **`MOM-0904` 레거시 `RequireAuth`의 신규 `access_token` 수용.** 레거시는 `session_token`
   쿠키 하나만 읽으므로(`momens-api/internal/platform/httpx/middleware.go:74`) 이것 없이
   로그인만 전환하면 레거시 Product API 전체가 401이 된다.
3. **레거시 `Logout`의 신규 쿠키 만료.** 레거시 `Logout`은 `session_token` 하나만 만료시킨다
   (`momens-api/internal/auth/handler.go:130-133`). FE의 `logout()`은 `VITE_API_BASE_URL`,
   즉 레거시로 가므로(`src/api/client.ts:140`) 1단계 이후 **로그아웃을 눌러도 인증 상태가
   유지된다.** 게이트 2가 어차피 레거시를 수정하므로 같은 변경 묶음에서 세 쿠키를 모두
   만료시킨다. `MOM-0904`에 범위를 기록했다.

## 4. 1단계 실행

```text
1. MOM-0873 확인 → 서명 키 동일성이 환경별로 기록됨
2. momens-api 배포 (RequireAuth 수용 + Logout 쿠키 만료)
   → 기존 session_token 경로가 회귀 없이 동작하는 것을 확인
3. FE: VITE_AUTH_LOGIN_URL만 신규 서버로 전환, 재빌드·재배포
4. 6절의 관측 창 진입
```

3단계 전에 2단계 배포가 prod에 반영된 것을 확인한다. **순서가 뒤집히면 신규 로그인 사용자가
레거시 Product API에서 401을 받는다.**

### 4.1 건드리지 않는 것

- **`/auth/me`·`PATCH /auth/me`는 계속 레거시로 간다.** 게이트 2 이후 `access_token`으로
  통과한다. `PATCH`는 `users` write지만 ADR-0016의 한시 예외 범위 안이다.
- **자동 refresh를 넣지 않는다.** 레거시 세션도 24시간, 신규 access TTL도 24시간이라 만료 시
  재로그인 동작이 지금과 같다.

### 4.2 `MOM-0906`을 통째로 적용하지 않는다

`MOM-0906`은 이 문서 이전에 세운 티켓이라 컷오버를 전부-아니면-전무 스위치로 보고 **env 두 개
전환과 경로 수정(`/auth/me` → `/api/me`, `/auth/logout` → `/api/auth/web/logout`)을 한 묶음**으로
잡고 있다.

**1단계가 가져가는 것은 `VITE_AUTH_LOGIN_URL` 전환뿐이다.** 경로 수정은 base가 신규 서버를
가리킬 때에만 의미가 있고, 먼저 적용하면 FE가 레거시 base에 `/api/me`를 불러 404가 난다. 4.1이
닫아 둔 전제를 깨뜨린다.

## 5. 2단계 게이트

**미해소 게이트가 셋이라 지금 착수할 수 없다.** 착수 전에 `MOM-0906`을 4.2에 맞춰 쪼갠다.

### G1 — retrieval 투영 공백 (`MOM-0898`)

레거시는 task·decision·blocker·memory 쓰기를 같은 트랜잭션에서 retrieval 문서로 인라인
투영한다(`momens-api/internal/bootstrap/app.go:155`, `internal/retrieval/projection.go`). 신규
서버는 outbox에 이벤트를 쌓는데(`TaskWriterImpl`, `MemoryWriterImpl`) `momens-worker`에 소비자가
없다.

웹 컷오버로 실제 움직이는 것은 **task와 memory 둘**이다. decision·blocker의 웹 endpoint는 원장
기준 폴백 전용이거나 호출처가 없다.

**이 실패는 조용하다.** write는 성공하므로 5xx도 401도 나지 않고 6절의 관측 창에서 잡히지
않는다. prod에서 소비가 동작하는 것을 확인한 뒤 2단계를 연다.

### G2 — `tasks`의 비-웹 레거시 writer (`MOM-0953`)

레거시에서 `task.Service`를 쓰는 write 경로는 둘이다 — MCP 서버(`internal/mcpserver/tools.go`)와
민수 액션(`internal/minsu/action/create_task.go`). `internal/slackbot/action.go`는 민수
`action.Dispatcher`에 위임하는 라우팅 래퍼이므로 별도 writer가 아니다.

두 표면은 ADR-0018로 컷오버 후에도 레거시에 남는다(원장 미결정 2번). FE base는 하나뿐이라 2단계
에서 웹 write가 함께 넘어가므로 `tasks`에 두 서버 writer가 공존한다. 원장의 「`tasks` target
writer 구현과 운영 활성화」가 별도 결정과 rollback 조건을 먼저 기록하도록 요구한다.

### G3 — source provider OAuth 미배선 (`MOM-0954`)

`H041`·`H082`는 원장의 실사용인데 신규 서버의 provider 자격 증명이 prod에 없다.
`configmap.yaml`·`secret.example.yaml`에 `MOMENS_SOURCE_OAUTH_*`가 없고
`application.yml:138-153`의 기본값이 전부 빈 문자열이다. `SourceInstallerImpl`의
`isConfigured()` 검사가 `SOURCE_PROVIDER_UNCONFIGURED`(**500**)를 던진다.

G1과 달리 5xx로 드러나지만, 2단계에서 base를 뒤집는 순간 **소스 연동이 통째로 죽는다.**

레거시 콜백은 `https://api.momens.works/source-connections/oauth/callback`이고 신규 서버는
`/api` 접두사가 붙어 주소가 다르므로 네 provider 콘솔의 등록도 바꿔야 한다. **두 주소를 병행
등록해 두고 전환한다** — 신규만 등록한 채 되돌리면 레거시 콜백이 깨진다.

### 함께 확인할 것

- **`MOM-0883` write 배포 후 검증 방침.** 2단계는 웹 write 전체를 옮기므로 이 결정이 없으면
  배포 후 확인 수단이 없다.
- 전략 문서 「롤백」의 데이터 호환성 6항목. 7.2를 따른다.

## 6. 관측

`momens-server`는 prod에서 지표를 프로세스 밖으로 내보내지 않는다. actuator는 `health`만
노출하고(`app/src/main/resources/application.yml:156-160`) tracing·OTLP export가 모두
비활성이며(`MOM-0834` 미착수) 클러스터에 수집기와 로그 집계가 없다. **시계열이 없다.** 볼 수
있는 것은 pod 로그와 ingress-nginx 액세스 로그뿐이고 둘 다 `kubectl`로만 보이며 보존되지 않는다.

`legacy_session_cookie` increase 0처럼 시계열이 필요한 판정은 이 문서가 다루지 않는다.
`MOM-0875`가 소유하고 `MOM-0834`를 선행으로 건다.

### 6.1 컷오버 판정 창

**전환을 실행한 사람이 그 자리에서 지켜본다.** 자리를 뜬 채로 전환하지 않는다.

| 신호 | 보는 곳 | 되돌리는 값 |
| --- | --- | --- |
| 전면 401 | 두 서버 pod 로그, ingress 액세스 로그의 상태 코드 | 로그인 성공 세션이 보호 경로에서 401을 받는 것이 **1건이라도** |
| 404 | 같은 곳 | 전환 전에 없던 경로의 404가 나타나면 |
| 5xx | 같은 곳 | 전환 전 기준선을 넘는 5xx가 지속되면 |
| write 실패 | pod 로그의 예외, DB constraint 위반 | 1건이라도 |

전면 401과 write 실패에 임계값을 두지 않는다. 둘 다 정상 상태에서 0이고, 1건이 보이면 그 뒤로
같은 실패가 모든 사용자에게 일어난다.

**2단계에서 404는 연쇄로 나타난다.** FE의 `loadWorkspaceSnapshotLegacy` 폴백은 snapshot이 404일
때만 동작하는데, 그 폴백이 부르는 H038·H039·H042·H044·H051은 신규 서버에 endpoint가 없다.
snapshot 하나가 404를 내면 5개 경로의 404가 함께 나타나므로, **404가 무더기로 보이면
snapshot부터 확인한다.**

관측 창의 길이는 실제 사용자 트래픽이 한 바퀴 도는 데 걸리는 시간으로 잡고, 전환 직후 집중
관측 뒤 같은 날 안에 한 번 더 확인한다. 로그가 보존되지 않으므로 **판정 근거는 그 자리에서
갈무리해 `MOM-0911`에 남긴다.**

## 7. 롤백

deploy rollback과 writer rollback을 구분한다(전략 문서 「롤백」). **웹 컷오버의 롤백은 서버 배포가
아니라 FE env를 되돌리는 것이다.**

### 7.1 1단계

`VITE_AUTH_LOGIN_URL`을 레거시로 되돌려 FE를 재빌드·재배포한다.

- 사용자는 레거시 로그인으로 `session_token`을 다시 발급받는다. 신규 서버는 ADR-0017에 따라 그
  쿠키를 계속 수용하므로 모바일·웹 어느 쪽도 끊기지 않는다.
- **`momens-api`의 3절 게이트 2·3 변경은 되돌리지 않는다.** 되돌리면 이미 신규 토큰을 든
  브라우저가 401을 받는다. 이 변경은 양방향으로 안전하다.
- `users`에 남은 신규 identity 행은 그대로 둔다. ADR-0016 예외가 두 writer 공존을 이미 허용해
  보상 절차가 필요 없다.

**1단계는 writer rollback이 필요 없고 routing rollback만으로 닫힌다.**

### 7.2 2단계

`VITE_API_BASE_URL`을 되돌리면 read는 닫히지만 그것만으로 전부 닫히지는 않는다.

- **provider redirect URI는 FE env가 아니다.** G3의 병행 등록을 해 두지 않고 신규 주소만
  등록한 채 되돌리면 레거시 콜백이 깨진다.
- **write는 데이터 호환성이 확인되지 않으면 되돌릴 수 없다.** 전략 문서의 6항목을 2단계
  게이트에서 항목별로 확인해 원장에 기록한다. 확인되지 않은 항목이 있으면 writer rollback
  가능하다고 적지 않는다.
- G1이 열린 채 2단계를 실행하면 outbox에 쌓인 이벤트는 롤백해도 사라지지 않는다. 되돌린 뒤
  레거시가 다시 인라인 투영하므로, 나중에 그 이벤트를 소비할 때 같은 문서를 두 경로가 갱신한다.

### 7.3 결정권

- **되돌림의 트리거는 전환을 실행한 사람이 당긴다.** 6.1의 값을 넘으면 상의 없이 되돌린다.
  되돌림은 되돌릴 수 있는 행위이고, 판단을 미루는 동안의 손실이 더 크다.
- 되돌린 뒤에 원인 분석과 재시도 여부를 상의한다.
- **FE 배포·롤백 경로가 아직 비어 있다.** `momens-fe`에는 CI 워크플로만 있고 배포 워크플로가
  없어 배포 주체와 소요 시간을 저장소에서 확인할 수 없다. **롤백 소요 시간이 곧 FE 재배포
  시간이므로 그 값 없이는 6.1의 관측 창을 설계할 수 없다.** 1단계 착수 전에 TL과 확인해 이 절을
  채운다.
