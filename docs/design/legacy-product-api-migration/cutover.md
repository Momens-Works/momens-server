# 웹 컷오버 전환 단위와 rollback runbook

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

## 1. 목적

[이관 원장](ledger.md)의 미결정 1번 — 웹 트래픽을 capability별로 혼합 전환할지, 신규 인증과
Product API를 한 번에 전환할지 — 를 확정하고, 전략 문서가 원칙만 적어 둔 롤백을 실행 가능한
절차로 남긴다.

[ADR-0018](../../adr/0018-transitional-legacy-acceptance-of-new-access-token.md)이 정한 것은
컷오버 **시점의 동작**이고, 이 문서가 정하는 것은 **전환의 단위와 순서**다.

## 2. 전환 스위치의 실제 위치

두 서버는 같은 host `api.momens.works`를 경로 prefix로 나눠 쓴다. ingress-nginx의 최장 prefix
매칭이 `/api/*`를 `momens-server`로, `/`를 `momens-api`로 보낸다
(`k8s/manifests/apps/momens-server/ingress.yaml`).

따라서 전환 스위치는 서버나 라우팅 규칙이 아니라 **FE 번들에 박히는 env 두 개와, 저장소 밖에
있는 provider 콘솔의 redirect URI**다.

| 스위치 | 무엇을 결정하는가 | 위치 |
| --- | --- | --- |
| `VITE_AUTH_LOGIN_URL` | 로그인 진입점. 브라우저 내비게이션이라 API client를 타지 않는다 | `src/api/config.ts:18` |
| `VITE_API_BASE_URL` | `MomensApiClient`의 모든 XHR. endpoint별 분기가 없다 | `src/api/config.ts:9`, `src/api/client.ts:58` |
| source provider redirect URI | 소스 연동 콜백이 어느 서버로 돌아오는가 | GitHub·Slack·Notion·Figma 콘솔 |

Vite가 빌드 타임에 앞의 두 값을 굽는다. **되돌리는 것은 설정 플립이 아니라 재빌드·재배포다.**
이것이 아래 롤백 절차의 소요 시간을 지배한다. 세 번째는 저장소에도 클러스터에도 없으므로
5.3에서 따로 다룬다.

두 env의 독립은 **조건부다.** `VITE_AUTH_LOGIN_URL`이 비어 있으면 `baseUrl`에서 파생된다.

```ts
// src/api/config.ts:18-20
authLoginUrl:
  import.meta.env.VITE_AUTH_LOGIN_URL ||
  `${baseUrl.replace(/\/$/, '')}/auth/google/login`,
```

`.env.production`이 값을 명시하고 있어서 지금 독립이 성립하는 것이지 코드 구조가 독립인 것은
아니다. **이 문서의 2단계 분할 전체가 그 명시에 걸려 있으므로, 값을 비우거나 지우면 `baseUrl`
전환이 로그인 진입점까지 함께 옮긴다.**

## 3. 전환 단위 — 인증과 Product API를 2단계로 나눈다

**capability별 혼합 전환은 채택하지 않는다.** 웹이 실사용하는 Product API는 원장 기준으로 이미
전부 `implemented`이므로 혼합으로 얻을 것이 없고, `VITE_API_BASE_URL`이 단일 base라 혼합하려면
FE에 없는 endpoint별 라우팅 계층을 새로 만들어야 한다. aggregate 단일 writer 경계를 그 라우팅에
맞춰 찾는 비용도 함께 든다.

**대신 인증과 Product API를 2단계로 나눈다.** 두 env가 독립이므로 FE 라우팅 계층 없이 나눌 수
있고, 한 세션이 두 서버의 Product API를 섞어 부르지 않으므로 혼합 트래픽이 아니다.

나누는 이유는 두 단계의 위험이 서로 다른 종류이고, **2단계에만 미해소 게이트가 있기 때문**이다.

| | 1단계 인증 | 2단계 Product API |
| --- | --- | --- |
| 뒤집는 스위치 | `VITE_AUTH_LOGIN_URL` | `VITE_API_BASE_URL` + provider redirect URI |
| 움직이는 writer | `users` | 나머지 전 aggregate |
| 주 위험 | 서명 키 불일치로 인한 전면 401 | retrieval 투영 공백, `tasks` 두 writer, source OAuth 미배선 |
| 선행 게이트 | 4절 | 5절 |
| 착수 가능 여부 | 가능 | 게이트 미해소 |

1단계가 움직이는 `users` 두 writer 공존은
[ADR-0016](../../adr/0016-user-identity-key-google-sub.md)이 이미 한시 예외로 승인한 상태이며
원장의 「`users` writer 한시적 예외」가 유지 조건을 적어 두었다. 1단계는 그 예외 밖으로 나가지
않는다.

## 4. 1단계 — 인증 전환

### 4.1 선행 게이트

순서대로 닫는다.

1. **`MOM-0873` 두 서버 JWT 서명 키 동일성 확인.** 확인 대상은 리포에 불변식으로 적혀 있다 —
   `k8s/manifests/apps/momens-server/secret.example.yaml:25-28`의
   *"TRANSITION INVARIANT: keep this EQUAL to momens-api's JWT_SECRET during the dual-run"*.
   값이 다르면 전환 즉시 웹 전체가 401을 받는다. 테스트로 잡히지 않는 실패 모드이므로 배포된
   시크릿 값을 환경별로 직접 확인한다.
2. **`MOM-0904` 레거시 `RequireAuth`의 신규 `access_token` 수용.** 레거시는 `session_token`
   쿠키 하나만 읽으므로(`momens-api/internal/platform/httpx/middleware.go:74`) 이것 없이
   로그인만 전환하면 레거시 Product API 전체가 401이 된다.
3. **레거시 `Logout`의 신규 쿠키 만료.** 아래 4.2를 따른다.

### 4.2 로그아웃 구멍과 그 처리

FE의 `logout()`은 `MomensApiClient`를 타므로 `VITE_API_BASE_URL`, 즉 **레거시**로 간다
(`src/api/client.ts:140`). 그런데 레거시 `Logout`은 `session_token` 하나만 만료시킨다
(`momens-api/internal/auth/handler.go:130-133`).

로그인만 전환하면 브라우저가 든 것은 신규 서버의 `access_token`·`refresh_token`인데 레거시는
그것을 지우지 않고, 위 게이트 2로 레거시가 그 토큰을 수용하게 된 뒤이므로 **로그아웃을 눌러도
인증 상태가 유지된다.**

`MOM-0905`와 방향만 반대인 같은 문제다. 레거시 `Logout`이 세 쿠키를 모두 만료시키게 한다.
게이트 2가 어차피 레거시를 수정하므로 같은 변경 묶음에서 처리하고, FE에 endpoint별 라우팅을
만들지 않는다.

### 4.3 회귀가 아닌 것

- **자동 refresh 부재.** 레거시 세션도 24시간, 신규 access TTL도 24시간이라 만료 시 재로그인
  동작은 지금과 같다. `MOM-0906`이 자동 refresh를 제외 범위로 둔 근거와 같다.
- **`/auth/me`·`PATCH /auth/me`가 레거시로 가는 것.** 게이트 2 이후 `access_token`으로
  통과한다. `PATCH`는 `users` write지만 ADR-0016 예외 범위 안이다.

### 4.4 전환 절차

```text
1. MOM-0873 확인 → 서명 키 동일성이 환경별로 기록됨
2. momens-api 배포 (RequireAuth 수용 + Logout 쿠키 만료)
   → 배포 후 기존 session_token 경로가 회귀 없이 동작하는 것을 확인
3. FE: VITE_AUTH_LOGIN_URL만 신규 서버로 전환, 재빌드·재배포
4. 6절의 관측 창 진입
```

3단계 전에 2단계 배포가 prod에 반영된 것을 확인한다. 순서가 뒤집히면 신규 로그인 사용자가
레거시 Product API에서 401을 받는다.

### 4.5 `MOM-0906`은 1단계에 통째로 적용하지 않는다

`MOM-0906`은 이 문서 이전에 세운 티켓이라 컷오버를 전부-아니면-전무 스위치로 보고 **env 두 개
전환과 경로 수정(`/auth/me` → `/api/me`, `/auth/logout` → `/api/auth/web/logout`)을 한 묶음**으로
잡고 있다. 그대로 배포하면 1단계 상태에서 FE가 `/api/me`를 부르는데 base는 아직 레거시이므로
`https://api.momens.works/api/me`가 아니라 레거시의 없는 경로가 되어 404가 난다.

**1단계가 가져가는 것은 `VITE_AUTH_LOGIN_URL` 전환뿐이다.** `VITE_API_BASE_URL` 전환과 경로
수정은 둘 다 2단계에 속한다. 경로 수정은 base가 신규 서버를 가리킬 때에만 의미가 있고, 그 전에
적용하면 4.3이 "회귀가 아니다"라고 닫아 둔 `/auth/me`·`/auth/logout`의 레거시 경유를 깨뜨린다.

## 5. 2단계 — Product API 전환

**미해소 게이트가 셋 있어 지금 착수할 수 없다.** 이 문서는 게이트를 명시하는 데까지만 간다.
2단계 착수 전에 `MOM-0906`을 4.5에 맞춰 쪼갠다.

### 5.1 G1 — retrieval 투영 공백

레거시는 task·decision·blocker·memory 쓰기를 같은 트랜잭션에서 retrieval 문서로 인라인
투영한다(`momens-api/internal/bootstrap/app.go:155`, `internal/retrieval/projection.go`).
신규 서버는 대신 outbox에 이벤트를 쌓는다(`TaskWriterImpl`, `MemoryWriterImpl`). 그런데
`momens-worker`에는 outbox 소비자가 없다.

따라서 `VITE_API_BASE_URL`을 뒤집는 순간부터 그 이후의 task·memory 변경이 검색 인덱스에
반영되지 않는다. **5xx도 401도 나지 않으므로 6절의 관측 창에서 잡히지 않고 사용자 신고로만
드러난다.**

`MOM-0898`(worker 공통 outbox consumer 기반)이 이 게이트다. prod에서 소비가 동작하는 것을
확인한 뒤 2단계를 연다.

레거시가 인라인 투영하는 것은 task·decision·blocker·memory 넷이지만 **웹 컷오버로 실제
움직이는 것은 task와 memory 둘이다.** decision·blocker의 웹 endpoint는 원장 기준 폴백 전용이거나
호출처가 없어 컷오버 범위 밖이다.

### 5.2 G2 — `tasks`의 비-웹 레거시 writer

레거시에서 `task.Service`를 쓰는 곳은 웹 핸들러만이 아니다. 구분되는 write 경로는 둘이다 —
MCP 서버(`create_task`·`update_task`·`create_comment`와 milestone 3종,
`internal/mcpserver/tools.go`)와 민수 액션(`internal/minsu/action/create_task.go`). slackbot의
`internal/slackbot/action.go`는 `routingAnswerer`로 민수 `action.Dispatcher`에 위임하는 라우팅
래퍼이며 `task.Service`를 직접 쓰지 않는다. 별도 writer가 아니라 민수 액션의 Slack 진입
표면이다.

이 표면들은 ADR-0018로 컷오버 후에도 레거시에 남는다(원장 미결정 2번).

FE base는 하나뿐이므로 2단계에서 웹 write는 반드시 함께 넘어가고, 그 결과 `tasks`에 두 서버
writer가 공존한다. 원장의 「`tasks` target writer 구현과 운영 활성화」가 이 상황에 대해 암묵적
예외가 아니라 별도 결정과 rollback 조건을 먼저 기록하도록 요구한다.

`MOM-0953`이 이 게이트다. **이 결정은 이 문서의 범위가 아니다.** 웹 컷오버가 만드는 문제가 아니라 드러내는 문제이고 —
지금도 레거시 안에서 셋이 같은 aggregate를 쓴다 — 판단에 MCP/OAuth 표면 이관(원장 미결정 2번)이
얽힌다. 2단계의 선행 게이트로만 걸고 별도 작업으로 뺀다.

### 5.3 G3 — source provider OAuth가 prod에 배선되지 않았다

`H041`(소스 연결 설치)과 `H082`(provider 콜백)는 원장의 실사용이다. 그런데 신규 서버의 provider
OAuth는 prod에 값이 없다. `k8s/manifests/apps/momens-server/configmap.yaml`과
`secret.example.yaml` 어디에도 `MOMENS_SOURCE_OAUTH_*`가 없고, `application.yml:138-153`의
기본값이 전부 빈 문자열이다.

`OAuthProviderRegistry`는 provider를 찾아 주지만 자격 증명이 빈 채로 온다. 막는 것은
`SourceInstallerImpl.configuredProvider`의 `isConfigured()` 검사이고, 걸리면
`SOURCE_PROVIDER_UNCONFIGURED`를 던진다. 이 코드는 **500**이다
(`modules/source/src/main/java/works/momens/server/source/SourceErrorCode.java:18`).

**G1과 성격이 다르다.** G1은 조용히 낡지만 G3는 6.3의 5xx 신호에 잡힌다. 그래도 게이트인 이유는
2단계에서 `VITE_API_BASE_URL`을 뒤집는 순간 **소스 연동 기능이 통째로 죽기 때문**이고, 관측으로
아는 것과 사전에 막는 것은 다르기 때문이다.

**저장소 밖 작업이 딸려 있다.** 레거시 콜백은
`https://api.momens.works/source-connections/oauth/callback`이고
(`k8s/manifests/apps/momens-api/configmap.yaml:26`) 신규 서버는 `/api` 접두사가 붙으므로 주소가
다르다. 원장 H082 행이 이미 *"이관 시점에 provider 관리 화면에 등록된 redirect URI도 함께
변경해야 한다"* 고 적어 두었다. GitHub·Slack·Notion·Figma 네 콘솔의 등록이 2절의 세 번째
스위치다.

**두 주소를 병행 등록해 두고 전환한다.** provider가 허용하는 한 그렇게 해야 7.3의 롤백이
성립한다. 신규 주소만 등록한 채 되돌리면 레거시 콜백이 깨진다. 병행 등록 자체가 2단계 선행
절차이며, 컷오버가 안정된 뒤 레거시 주소를 지운다.

`MOM-0954`가 이 게이트다.

### 5.4 함께 확인할 것

- **write 배포 후 검증 방침(`MOM-0883`).** 2단계는 웹 write 전체를 옮기므로 이 결정이 없으면
  배포 후 확인 수단 없이 진행된다.
- 전략 문서 「롤백」의 데이터 호환성 6항목. 7.3을 따른다.

## 6. 관측

### 6.1 지금 볼 수 있는 것

`momens-server`는 prod에서 지표를 프로세스 밖으로 내보내지 않는다. actuator는 `health`만
노출하고(`app/src/main/resources/application.yml:156-160`), tracing·OTLP export는 모두
비활성이며(`MOM-0834` 미착수) 클러스터에 수집기와 로그 집계가 없다.

**시계열이 존재하지 않는다.** 컷오버 순간 볼 수 있는 것은 pod 로그와 ingress-nginx 액세스
로그뿐이고, 둘 다 `kubectl`로만 보이며 보존되지 않는다.

### 6.2 성격이 다른 두 창을 분리한다

원장이 "컷오버 관측 기간 미결정"으로 뭉뚱그린 것은 사실 두 개다.

| 창 | 판정 대상 | 필요한 것 | 소유 |
| --- | --- | --- | --- |
| 컷오버 판정 창 | 되돌릴지 말지 | 사람이 지켜보는 시간 | 이 문서 |
| 전환기 코드 제거 창 | `legacy_session_cookie` increase 0 | 시계열, 따라서 `MOM-0834` | `MOM-0875` |

컷오버 판정이 잡아야 할 실패는 전면 401, 404, 5xx, write 실패 넷이고 **전부 첫 요청부터 분
단위로 드러나므로 시계열이 필요 없다.** 두 창을 묶으면 지표 백엔드 배선이 컷오버의 선행
게이트가 되어 컷오버 전체가 `MOM-0834` 뒤로 밀린다. 분리하면 `MOM-0834`는 `MOM-0875`의 선행
이라는 제 위치에 남는다.

### 6.3 컷오버 판정 창

전환을 실행한 사람이 그 자리에서 지켜본다. 자리를 뜬 채로 전환하지 않는다.

| 신호 | 보는 곳 | 되돌리는 값 |
| --- | --- | --- |
| 전면 401 | `momens-api`·`momens-server` pod 로그, ingress 액세스 로그의 상태 코드 | 로그인 성공 세션이 보호 경로에서 401을 받는 것이 **1건이라도** 확인되면 |
| 404 | 같은 곳 | 전환 전에 없던 경로의 404가 나타나면 |
| 5xx | 같은 곳 | 전환 전 기준선을 넘는 5xx가 지속되면 |
| write 실패 | pod 로그의 예외, DB constraint 위반 | 1건이라도 |

전면 401과 write 실패에 임계값을 두지 않는 것은 의도한 것이다. 둘 다 정상 상태에서 0이고,
1건이 보이면 그 뒤로 같은 실패가 모든 사용자에게 일어난다.

**2단계에서 404는 연쇄로 나타날 수 있다.** FE의 `loadWorkspaceSnapshotLegacy` 폴백은 snapshot이
404일 때만 동작하는데, 그 폴백이 부르는 H038·H039·H042·H044·H051은 신규 서버에 endpoint가
없다(원장 기준 `traced`). 즉 **snapshot 하나가 404를 내면 폴백이 켜지면서 5개 경로의 404가 함께
나타난다.** 관측하는 사람이 원인을 다섯 개로 세지 않도록, 404가 무더기로 보이면 snapshot부터
확인한다.

관측 창의 길이는 실제 사용자 트래픽이 한 바퀴 도는 데 걸리는 시간으로 잡고, 전환 직후 집중
관측 뒤 같은 날 안에 한 번 더 확인한다. 로그가 보존되지 않으므로 **판정에 쓴 근거는 그 자리에서
갈무리해 이 문서 또는 `MOM-0911` 후속 작업에 남긴다.**

## 7. 롤백

### 7.1 deploy rollback과 writer rollback

전략 문서의 구분을 따른다. deploy rollback은 코드나 routing을 되돌리는 것이고, writer rollback은
aggregate writer를 레거시로 되돌리는 것이다. **웹 컷오버의 롤백은 서버 배포가 아니라 FE env를
되돌리는 것**이라는 점이 이 문서의 핵심이다.

### 7.2 1단계 롤백

`VITE_AUTH_LOGIN_URL`을 레거시로 되돌려 FE를 재빌드·재배포한다.

- 되돌린 뒤 사용자는 레거시 로그인으로 `session_token`을 다시 발급받는다. 신규 서버는
  ADR-0017에 따라 그 쿠키를 계속 수용하므로 모바일·웹 어느 쪽도 끊기지 않는다.
- **`momens-api`의 4.1 게이트 2·3 변경은 되돌리지 않는다.** 되돌리면 이미 신규 토큰을 든
  브라우저가 401을 받는다. 이 변경은 양방향으로 안전하다.
- `users`에 남은 신규 identity 행은 그대로 둔다. ADR-0016 예외가 두 writer 공존을 이미 허용하고
  있으므로 보상 절차가 필요 없다.

즉 1단계는 **writer rollback이 필요 없고 routing rollback만으로 닫힌다.** 2단계와 결정적으로
다른 점이다.

### 7.3 2단계 롤백

`VITE_API_BASE_URL`을 되돌리는 것으로 read는 닫히지만, 그것만으로 전부 닫히지는 않는다.

**source provider redirect URI는 FE env가 아니다.** 5.3의 병행 등록을 해 두지 않고 신규 주소만
등록한 채 되돌리면 레거시 콜백이 깨진다. 병행 등록이 되어 있으면 이 절의 롤백은 env 되돌리기로
닫히고, 안정된 뒤에 레거시 주소를 지운다.

**write는 데이터 호환성이 확인되지 않으면 되돌릴 수 없다.** 전략 문서의 6항목을 2단계 게이트에서 항목별로 확인하고 결과를 원장에
기록한다. 확인되지 않은 항목이 있으면 writer rollback 가능하다고 적지 않는다.

G1이 열려 있는 동안 2단계를 실행하면 outbox에 쌓인 이벤트는 롤백해도 사라지지 않는다. 되돌린
뒤 레거시가 다시 인라인 투영을 하므로, 나중에 그 이벤트를 소비할 때 같은 문서를 두 경로가
갱신하게 된다. 5.1을 게이트로 두는 이유에 이것도 포함된다.

### 7.4 결정권

- **전환 실행과 되돌림의 트리거는 그 전환을 실행한 사람이 당긴다.** 6.3의 값을 넘으면 상의
  없이 되돌린다. 되돌림은 되돌릴 수 있는 행위이고, 판단을 미루는 동안의 손실이 더 크다.
- 되돌린 뒤에 원인 분석과 재시도 여부를 상의한다.
- **FE 배포·롤백 경로는 아직 이 원장에 기록되어 있지 않다.** `momens-fe`에는 CI 워크플로만 있고
  배포 워크플로가 없어 배포 주체와 소요 시간을 저장소에서 확인할 수 없다. 1단계 착수 전에 TL과
  확인해 이 절을 채운다 — **롤백 소요 시간이 곧 FE 재배포 시간이므로, 그 값을 모르면 6.3의
  관측 창을 설계할 수 없다.**

## 8. 원장 반영

- 미결정 1번은 3절로 해소한다.
- 미결정 2번(MCP/OAuth 표면 이관)은 그대로 남으며 5.2의 선행이다.
- 「컷오버 관측 기간 미결정」은 6절로 해소한다.
