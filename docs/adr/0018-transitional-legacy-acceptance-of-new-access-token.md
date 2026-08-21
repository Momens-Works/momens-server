# 0018. 전환기 레거시의 신규 access token 수용

- 상태: Accepted
- 날짜: 2026-08-21
- 작성자: Kimgyuilli

## 맥락

웹 로그인이 신규 서버(`/api/auth/google/*`)로 전환되면 레거시 `momens-api`는 더 이상
`session_token`을 발급하지 않는다. 레거시 `RequireAuth`는 `session_token` 쿠키 하나만 읽으므로
(`internal/platform/httpx/middleware.go:73`) 그 순간 아직 이관되지 않은 레거시 보호 endpoint가
전부 `401`을 받는다. `Authorization` 헤더도, 다른 쿠키도 보지 않는다.

[이관 원장](../design/legacy-product-api-migration-ledger.md)으로 컷오버 시점에 실제로 끊기는
**웹 실사용** endpoint를 전수 확인하면 12개이고, 그중 7개는 이미 담당 이관 티켓이 있다.

| 미이관 웹 실사용 | 담당 |
| --- | --- |
| H019 `POST /workspaces` | `MOM-0897` |
| H037 `POST /workspaces/:id/projects`, H050 `POST /projects/:projectId/milestones` | `MOM-0866` |
| H067~H069·H071 (task↔memory·source-ref 연결) | `MOM-0868` |
| H009~H011, H035·H036 (MCP consent·grants) | 이관 경로 없음 (`MOM-0871`) |

이관 경로가 없는 것은 MCP consent·grants 5개뿐이다. 이 결정은 그 5개를 대상으로 하되, 아래
"적용 범위"에서 정하듯 구현은 레거시 보호 endpoint 전체에 걸린다.

MCP 연동 자체는 웹 세션과 무관하다는 점이 이 결정의 범위를 좁힌다.

- `/mcp` transport(H012)는 세션 쿠키가 아니라 자체 OAuth access token을 `Authorization` 헤더로
  검증한다(`internal/mcpserver/auth.go:53`).
- `POST /oauth/token`(H007)과 `POST /oauth/revoke`(H008)도 세션을 요구하지 않는다
  (`internal/bootstrap/router.go`의 `requireAuth` 그룹 바깥).
- MCP access TTL은 1시간, refresh TTL은 90일 회전이다(`internal/mcpauth/service.go:24-27`).

따라서 컷오버로 실제로 막히는 것은 **새 클라이언트 승인(consent)** 과 **웹 UI에서의 grant 폐기**
둘이다. 기존 연동은 계속 동작한다.

전환 준비 상태로 확인한 사실은 다음과 같다.

- 두 서버는 같은 host `api.momens.works`를 path prefix로 나눠 쓴다(`/api`는 신규, `/`는 레거시).
  FE는 `app.momens.works`이고 same-site라 `SameSite=Lax` 쿠키가 그대로 전달된다.
- 신규 `access_token` 쿠키는 `Path=/`·host-only·`Lax`·`Secure`다
  (`WebAuthCookies.java`의 `ACCESS_PATH`). 레거시 `session_token`도 `Path=/`·host-only·`Lax`다
  (`internal/auth/handler.go:188`). **즉 컷오버 후 브라우저는 이미 `access_token`을 레거시 경로로도
  보내고 있다. 레거시가 읽지 않을 뿐이다.**
- 두 토큰은 구조적으로 같다. 모두 HS256이고 claim은 `sub`(userId)·`iat`·`exp`뿐이며 서명 키를
  공유한다([ADR-0017](0017-transitional-legacy-session-token-acceptance.md)).
- 만료 동작도 같다. 레거시 세션은 24시간(`JWT_EXPIRY_HOURS` 기본값, 쿠키 `maxAge` 86400)이고 신규
  access TTL도 24시간이다(`application.yml`의 `momens.auth.access-ttl`).

[이관 전략](../design/legacy-product-api-migration-strategy.md)은 혼합 트래픽 호환 방식 세 가지 중
하나를 명시적으로 고르고 ADR로 남기도록 규정한다. 이 결정은 그중 **호환 방식 2**이며,
[ADR-0003](0003-auth-session-transport-model.md)의 한시적 예외이므로 별도 기록이 필요하다.
ADR-0017이 신규 서버가 레거시 쿠키를 수용하는 방향을 정했고, 이 ADR은 그 반대 방향을 정한다.

## 결정

**레거시 `momens-api`가 전환기 동안 신규 `access_token` 쿠키를 세션으로 수용한다**(이관 전략의
호환 방식 2).

1. `RequireAuth`의 조회 순서는 `access_token` 쿠키 → `session_token` 쿠키다. `Authorization`
   헤더는 보지 않는다.
2. 파서와 서명 키는 바꾸지 않는다. 같은 키와 같은 claim을 쓰므로 기존 HS256 파서가 그대로
   검증한다.
3. **적용 범위는 레거시 보호 endpoint 전체다**(`MOM-0904`). `requireAuth`는 12개 라우트 그룹과
   `/auth/me` 2개 route가 공유하는 클로저 하나이고(`internal/bootstrap/router.go:50`), MCP grants
   2개는 `workspaceGroup` 안에 등록돼 있어(`router.go:113-114`) 좁히려면 미들웨어 신설과 라우트
   재배치가 필요하다.
4. **신규 서버의 `POST /api/auth/web/logout`이 `session_token`도 만료시킨다.** 현재는
   `access_token`과 `refresh_token`만 지우므로(`WebAuthController.java`의 `webLogout`), 컷오버
   직후 아직 유효한 레거시 쿠키를 든 사용자는 신규 서버로 로그아웃해도 레거시 경로에서 최대
   24시간 인증된 채 남는다. 같은 host·`Path=/`라 만료 `Set-Cookie` 한 줄로 닫는다(`MOM-0905`).
5. **제거 조건은 레거시 `momens-api` 종료다.** 별도 관측 지표를 만들지 않는다. 근거는 아래
   "대안"에 적었다.

## 대안

- **MCP 5개 endpoint를 신규 서버로 조기 이관한다**(호환 방식 없이 해결): `momens-server`에는
  `oauth_*` 테이블도 엔티티도 없어 6개 테이블(`migrations/000019_mcp_oauth.sql`)을 새로 만들어야
  한다. 더 큰 문제는 `authorize`(H006)와 `token`(H007)이 레거시에 남는 동안
  `oauth_interactions`·`oauth_grants`·`oauth_authorization_codes`의 writer가 두 서버로 갈린다는
  점이다. 이관 원장의 aggregate별 단일 writer 규칙과 충돌하고, 미결정 2번(MCP/OAuth 경계)을 이
  결정에서 강제로 열게 된다. 기각.
- **컷오버 관측 기간 동안 MCP 설정 화면을 동결한다**: 서버 변경이 없고, 위 맥락대로 기존 연동은
  살아 있어 비용이 작아 보인다. 그러나 동결 기간이 곧 원장 미결정 8번의 "컷오버 관측 기간"인데
  아직 정해지지 않아 사실상 무기한 동결을 승인하게 된다. 더 결정적으로, 막히는 것 중 하나가
  **grant 폐기**다. 연동을 끊고 싶은 사용자가 끊을 수 없는 상태를 관측 기간 내내 유지하는 것은
  기능 동결이라기보다 보안 관련 능력의 일시 상실이다. 기각.
- **FE가 MCP 5개 경로만 레거시 base URL로 계속 호출한다**: 성립하지 않는다. 문제는 라우팅이
  아니라 인증이라, 레거시로 보내도 세션이 없어 `401`이다. 브리지 없이는 어떤 FE 라우팅으로도 풀
  수 없다. 기각.
- **`session_token`을 먼저 보고 `access_token`을 폴백으로 둔다**: 변경이 가장 작아 보이지만,
  컷오버 구간에 두 쿠키가 동시에 남고 주체가 다를 수 있다. 신규 로그인에서 다른 Google 계정을
  골랐다면 `access_token`은 B, 남은 `session_token`은 A인데 이때 A로 인증하면 틀린 사용자가 된다.
  기각.
- **`Authorization` 헤더까지 수용한다**(ADR-0017과 완전 대칭): 레거시는 브라우저 전용 표면이라
  헤더를 보내는 호출자가 없다. 차등 비교 하네스도 두 서버 모두에 `Cookie: session_token=...`으로
  보낸다(`scripts/legacy-diff/diff.sh:196`). 대칭성 외에 근거가 없어 쓰이지 않는 수용면만 남는다.
  기각.
- **레거시에 해석 경로 관측 지표를 둔다**(ADR-0017의 `MOM-0872`와 대칭): 레거시에는 metrics 기반이
  없어 이 결정 하나를 위해 관측 기반을 새로 세워야 한다. 더 근본적으로, 지표의 목적인 "언제 뗄
  것인가"가 이 브리지에는 적용되지 않는다. ADR-0017의 fallback은 **신규 서버에** 남는 코드라 제거
  시점을 판단해야 하지만, 이 브리지는 **레거시 안에** 있어 레거시와 함께 사라진다. MCP/OAuth
  표면이 신규로 이관돼도 나머지 레거시 endpoint 때문에 남아야 하므로, 레거시 종료 이전에 뗄 수
  있는 시점이 없다. 기각.

## 결과

- 웹 로그인 컷오버가 MCP consent·grants의 이관을 기다리지 않아도 된다. grant 폐기 능력을 잃지
  않는다.
- 구현이 작다. 쿠키가 이미 레거시 경로에 도달하므로 `RequireAuth`의 읽기 한 단계 추가로 끝나고,
  FE·쿠키 속성·인프라 변경이 없다.
- **전역 적용의 부수효과로 미이관 웹 실사용 7개(H019, H037, H050, H067~H069, H071)도 컷오버 후
  함께 살아난다.** 컷오버를 이관 완료와 분리할 수 있다는 뜻이며, 작게 나눠 전환한다는 이관 전략과
  맞다. 다만 이관 압박이 미들웨어로 강제되지 않으므로 원장과 티켓으로 관리한다.
- ADR-0003의 "레거시 단일 세션 쿠키 폐기"는 유지된다. 이 ADR은 폐기 시점까지의 수용을 정할 뿐
  세션 모델을 되돌리지 않는다. 전환기 예외가 ADR-0017에 이어 하나 더 늘어난다.
- **두 방향의 제거 조건이 다르다.** ADR-0017의 신규 서버 fallback은 지표 기반으로 제거하고
  (`MOM-0875`), 이 ADR의 레거시 브리지는 레거시 종료와 함께 사라진다. 결정 4의 로그아웃
  `Set-Cookie`는 신규 서버 코드이므로 `MOM-0875` 범위에 속한다.
- 웹 컷오버 자체는 FE 변경을 동반한다. `momens-fe`는 baseUrl 하나로 모든 경로를 호출하고
  `/auth/me`·`/auth/logout`이 신규 서버의 `/api/me`·`/api/auth/web/logout`과 어긋난다(`MOM-0906`).
- 레거시가 신규 쿠키 이름을 알게 된다. 레거시 코드라 종료 시 함께 사라지지만, 그때까지는 두
  서버가 서로의 쿠키 이름을 아는 상태가 유지된다.
- **레거시 로그인 진입점이 컷오버 후에도 살아 있다.** `api.momens.works/auth/google/login`은 그대로
  동작하므로, 북마크나 설정 누락으로 그 경로를 타면 `session_token`이 다시 발급되고 결정 4가 닫은
  창이 다시 열린다. 레거시 종료 또는 그 진입점 차단으로만 완전히 닫힌다.
- 만료 동작은 컷오버 전후가 같다(양쪽 24시간). `momens-fe`에 refresh 호출이 없다는 사실은 이
  전환의 회귀가 아니라 기존과 같은 상태다.
