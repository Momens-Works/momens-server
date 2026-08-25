# prod 운영 준비 대장

이 대장은 prod 배포 전에 함께 확인해야 하는 필수 설정과 파일 밖 운영 의무를 한곳에 모읍니다.

- **선언 구간**: prod 필수 환경변수의 주입 위치와 반영 상태를 사람이 관리합니다. CI가
  `application.yml`과 `application-prod.yml`의 기본값 없는 placeholder와 정확히 일치하는지 검사하고,
  `required` 상태가 남은 릴리스를 차단합니다.
- **수기 구간**: 코드로 감지할 수 없는 외부 등록물과 배포 순서 의무를 사람이 확인합니다.

## 스키마 구간은 없습니다

prod 스키마 주도권이 이 서버로 넘어오면서([ADR-0019](adr/0019-prod-schema-ownership-transfer.md))
이 문서가 추적하던 **스키마 반영 구간을 폐지했습니다.** 반영을 기다릴 다른 저장소가 없어졌기
때문입니다. 이제 마이그레이션은 배포 때 이 서버의 Flyway가 직접 적용하고, 잘못된 마이그레이션은
그 시점에 Flyway가 막습니다. dev가 이미 그렇게 동작합니다.

마이그레이션 첫 줄의 `-- prod-schema:` 헤더도 함께 폐지했습니다. 기존 파일에 남아 있는 헤더는
Flyway checksum 때문에 지우지 않고 역사적 주석으로 둡니다. **새 마이그레이션에는 달지 않습니다.**

경위와 부트스트랩 절차는 [prod 스키마 주도권 이전 설계](design/prod-schema-ownership-transfer.md)가
소유합니다.

## prod 필수 설정 선언

공통 설정과 prod 프로필을 함께 적용했을 때 기본값이 없는 `${VAR}`만 강제합니다. local/dev 전용
설정은 prod provisioning 의무가 아니므로 이 선언과 스캔 대상에서 제외합니다.

상태는 `required`(prod 반영 필요, 릴리스 차단) 또는 `applied`(prod 반영 완료)입니다. 실제 값이나
secret 이름은 기록하지 않습니다. `applied`는 운영 환경 반영을 확인한 사람이 갱신하는 선언이며,
CI가 실제 Secret·ConfigMap의 존재나 값을 조회해 증명하지는 않습니다.

<!-- BEGIN DECLARATION: prod-required-config -->
| 환경변수 | 주입 위치 | prod 상태 |
| --- | --- | --- |
| `DATABASE_PASSWORD` | `secret` | `applied` |
| `DATABASE_URL` | `secret` | `applied` |
| `DATABASE_USERNAME` | `secret` | `applied` |
| `MOMENS_AUTH_GOOGLE_AUDIENCES` | `secret` | `applied` |
| `MOMENS_AUTH_GOOGLE_CLIENT_ID` | `secret` | `applied` |
| `MOMENS_AUTH_GOOGLE_CLIENT_SECRET` | `secret` | `applied` |
| `MOMENS_AUTH_GOOGLE_REDIRECT_URI` | `configmap` | `applied` |
| `MOMENS_AUTH_JWT_SECRET` | `secret` | `applied` |
| `MOMENS_AUTH_WEB_FAILURE_REDIRECT_URI` | `configmap` | `applied` |
| `MOMENS_AUTH_WEB_SUCCESS_REDIRECT_URI` | `configmap` | `applied` |
<!-- END DECLARATION: prod-required-config -->

## 수기 prod 의무

이 구간은 자동 생성하거나 릴리스 게이트로 차단하지 않습니다. 코드에서 감지할 수 없거나, 차단하면
역방향 배포 순서가 교착되는 의무를 기록합니다. 릴리스 전에 상태와 근거를 사람이 다시 확인합니다.

| 의무 | 현재 상태 | 확인 근거·다음 행동 |
| --- | --- | --- |
| **MOM-0909 부트스트랩 적용 전 `main` 릴리스 금지** | `required` | 스키마 릴리스 게이트를 MOM-0910에서 폐지했습니다(교착 해소, [설계 6절](design/prod-schema-ownership-transfer.md)). 그 게이트가 막던 것 중 하나는 크로스 리포 의무가 아니라 **기동 실패**였고, 그 보호가 지금은 없습니다. 부트스트랩 적용 전에 `develop` → `main` 릴리스가 나가면 미반영 16건 때문에 `ddl-auto=validate`에서 기동에 실패하고, `startupProbe` 150초 뒤 `rollout status --timeout=300s`가 깨집니다. `deploy-service.sh`에 `rollout undo`가 없어 Ready 되지 못한 ReplicaSet이 수동 정리 전까지 남습니다. 사용자 영향은 없습니다(`replicas: 1` + `maxUnavailable=0`으로 옛 Pod 유지, FE 미cutover). **부트스트랩이 적용되면 이 행을 제거합니다.** |
| **MOM-0909 부트스트랩 DDL 선행 권한** | `승인 대기` | 부트스트랩이 성립하려면 관리자 조치 세 가지가 먼저 필요하고 **순서가 있습니다.** ① `GRANT momens_server TO postgres WITH SET TRUE` — 비-superuser 인 Supabase `postgres` 가 `ALTER TABLE ... OWNER TO` 를 하려면 대상 role 로 `SET ROLE` 할 수 있어야 합니다. 소유권 이전과 심기가 둘 다 여기에 의존하므로 가장 먼저 실행합니다(prod 실측: `admin_option = true` 라 통과합니다). ② `scripts/prod-ownership-transfer.sh --generate` 로 만든 소유권 일괄 이전 SQL — 서버가 마이그레이션 파일을 갖는 레거시 테이블 20개입니다. `ALTER TABLE` 은 GRANT 체계 밖이라 `GRANT ALL PRIVILEGES` 로도 얻을 수 없고, ADR-0019 의 최종 상태이므로 한 번에 받습니다. 생성물이 이전과 `GRANT ... TO postgres` 재발급을 짝으로 묶습니다 — **재발급이 빠지면 레거시가 끊깁니다.** ③ `GRANT USAGE ON SCHEMA extensions TO momens_server` + `ALTER ROLE momens_server SET search_path = "$user", public, extensions` — Supabase 는 `uuid-ossp` 를 `extensions` 에 두는데 실행 집합 2건이 한정 없이 `uuid_generate_v4()` 를 호출합니다. **둘 다** 필요하며 파일 수정은 체크섬 때문에 불가능합니다. 셋 다 `scripts/prod-twin` 리허설이 없을 때의 실패를 재현했습니다. 소유권을 나중에 되돌린다면 20개 전부에 DML 재발급이 함께 가야 합니다(설계 7절). **부트스트랩이 적용되면 이 행을 제거합니다.** |
| **MOM-0909 이력 테이블 소유자** | `스크립트가 처리` | 심기 후 `flyway_schema_history` 의 소유자가 `momens_server` 여야 합니다. Postgres 는 테이블을 만든 role 을 소유자로 두는데 실행 창구인 Supabase SQL Editor 는 `postgres` 세션이고, `momens_server` 로 psql 을 여는 선택지는 없습니다(비밀번호를 아는 사람이 없습니다). `--generate` 생성물이 트랜잭션 안에서 `ALTER TABLE flyway_schema_history OWNER TO momens_server` 를 수행하므로 **생성물을 손대지 않고 그대로 실행하면 됩니다.** 그 줄이 빠지면 심기는 성공하고 다음 기동이 `permission denied for table flyway_schema_history` 로 죽습니다 — 심기 시점에는 신호가 없습니다. `prod-flyway-bootstrap-verify-test.sh` 가 CI 에서 그 줄을 지킵니다. **부트스트랩이 적용되면 이 행을 제거합니다.** |
| MOM-0836 `users.email` UNIQUE 제거 | `required` | 서버 코드를 먼저 배포한 뒤 제약을 제거합니다. 선행 배포는 `MOM-0914`에서 `ON CONFLICT (email)`을 제거하는 작업입니다. 이후 웹 로그인 요청이 신규 서버로 전환되어 `momens-server`가 `users`에 쓰는 유일한 서버가 되고, `MOM-0908`에서 마이그레이션 소유 레포지토리가 정해지면 `MOM-0836`에서 제약을 제거합니다. 선행 배포와 제약 제거가 모두 완료되면 해당 행을 갱신합니다 |
| Google OAuth redirect URI 등록 | `확인 필요` | Kubernetes 값은 `https://api.momens.works/api/auth/google/callback`입니다. Google Cloud 콘솔 등록 상태는 저장소에서 확인할 수 없습니다 |
| 모바일·웹 client ID와 audiences 일치 | `확인 필요` | 실제 secret 값과 Google OAuth client ID 목록을 배포 전에 대조합니다 |
| FCM 프로젝트·ADC 자격증명 | `비활성` | push 기본값은 꺼져 있습니다. 활성화할 때 `MOMENS_NOTIFICATION_PUSH_FIREBASE_PROJECT_ID`와 ADC를 확인합니다 |
| Minsu GCP 프로젝트·리전·ADC | `비활성` | task draft와 비동기 enroll/drain 기본값은 꺼져 있습니다. 활성화 조건은 관련 설계 문서가 소유합니다 |
| source provider redirect URI 등록 | `확인 필요` | GitHub, Slack, Notion, Figma의 관리 화면에 `https://api.momens.works/api/source-connections/oauth/callback`을 등록해야 합니다. 신규 경로에는 레거시 경로에 없는 `/api` 접두사가 포함되어 있어 서로 다른 주소입니다. 기존 주소를 삭제하지 않고 신규 주소를 추가하면 redirect URI 설정만 변경해 레거시 경로로 되돌릴 수 있습니다. 등록 여부는 레포지토리에서 확인할 수 없습니다. |
| 초대 이메일 발송 설정 | `비활성` | 이메일 발송은 기본적으로 비활성화되어 있어 초대를 생성해도 이메일이 발송되지 않습니다. 활성화하려면 `EMAIL_PROVIDER=resend`와 함께 `RESEND_API_KEY`, `EMAIL_FROM`, `EMAIL_REPLY_TO`, `APP_INVITE_URL`을 모두 주입해야 합니다. 필수 설정 중 하나라도 비어 있으면 서버 기동에 실패합니다 |
| source provider 자격 증명과 토큰 키 주입 | `비활성` | 관련 설정의 기본값이 모두 비어 있어 현재는 어떤 provider도 설정되지 않은 상태입니다. 이 상태에서는 source 연결을 시작할 수 없지만 서버 기동에는 영향을 주지 않습니다. 활성화하려면 provider 네 곳의 Client ID와 secret, `MOMENS_SOURCE_OAUTH_REDIRECT_URI`, `MOMENS_SOURCE_OAUTH_TOKEN_KEY`를 주입해야 합니다. 토큰 키는 base64로 디코딩했을 때 32바이트여야 하며, `momens-worker`에서 사용하는 값과 같아야 합니다. |
| source OAuth state 서명 비밀 | `비활성` | 전환이 완료될 때까지 레거시의 인증 JWT 시크릿과 같은 값을 `MOMENS_SOURCE_OAUTH_STATE_SECRET`에 주입해야 합니다. 값이 다르면 한쪽 서버가 발급한 state를 다른 서버가 검증하지 못해 전환 중 source 연결이 실패합니다. 전환이 끝난 뒤에는 이 값을 독립적으로 교체할 수 있습니다. |
| DNS·ingress·TLS 인증서 | `응답 확인` | 2026-08-13에 `https://api.momens.works/api/health`의 TLS 응답(HTTP 401)을 확인했습니다. 애플리케이션 readiness를 뜻하지는 않습니다 |
