# prod 운영 준비 대장

prod 배포 전에 함께 확인해야 하는 스키마, 필수 설정, 파일 밖 운영 의무를 한곳에 모읍니다.

- **생성 구간**: 스키마 헤더가 정본입니다. 직접 수정하지 않고
  `scripts/prod-schema-ledger.sh --write`로 갱신합니다.
- **선언 구간**: prod 필수 환경변수의 주입 위치와 반영 상태를 사람이 관리합니다. CI가
  `application.yml`과 `application-prod.yml`의 기본값 없는 placeholder와 정확히 일치하는지 검사하고,
  `required` 상태가 남은 릴리스를 차단합니다.
- **수기 구간**: 코드로 감지할 수 없는 외부 등록물과 배포 순서 의무를 사람이 확인합니다.

<!-- BEGIN GENERATED: prod-schema -->
## 스키마 반영

prod는 레거시 `momens-api`와 공유 DB를 쓰는 전환기라 이 서버의 Flyway가 꺼져 있고
`ddl-auto: validate`로 매핑만 검증합니다([데이터](rules/persistence.md)). 따라서 서버가 추가한
신규 스키마는 **반영 담당 저장소**의 마이그레이션으로 prod에 반영해야 하고, 반영되지 않으면 매핑
검증에 실패해 **애플리케이션이 기동하지 않습니다.**

담당 저장소는 스키마 소유자에 따라 갈립니다. 레거시가 소유한 스키마는 `momens-api`가 반영하지만,
worker가 생산하는 테이블(`signals` 계열, ADR-0007)의 반영 위치는 아직 확정되지 않았습니다.

이 문서는 그 반영 상태를 마이그레이션 단위로 추적합니다. 미반영 한 줄은 **"이 파일을 그대로
옮긴다"가 아니라 "이 파일이 만드는 객체 중 prod에 없는 것이 있다"**는 뜻입니다. 한 파일이 여러
객체를 건드리고 그중 일부만 레거시에 없을 수 있으므로, 반영 범위는 반영 시점에 객체 단위로
대조해 정합니다.

## 미반영 — 16건

prod에 반영해야 하고 아직 반영 PR이 없는 항목입니다. 릴리스 PR에서 차단됩니다.

| 마이그레이션 | 모듈 | 근거 |
| --- | --- | --- |
| `V20260707100000__create_signals.sql` | `signal` | MOM-0840 |
| `V20260707110000__create_signal_actions.sql` | `signal` | MOM-0840 |
| `V20260707120000__add_task_detail_and_checklist.sql` | `project` | MOM-0840 |
| `V20260707130000__create_signal_evidence.sql` | `signal` | MOM-0840 |
| `V20260707150000__task_role_single_value.sql` | `project` | MOM-0840 |
| `V20260713120000__add_signal_evidence_semantic_fields.sql` | `signal` | MOM-0840 |
| `V20260714090000__create_outbox_events.sql` | `outbox` | MOM-0840 |
| `V20260714091000__add_task_origin.sql` | `project` | MOM-0840 |
| `V20260715090000__task_role_drop_not_null.sql` | `project` | MOM-0840 |
| `V20260716090000__add_task_minsu_fields.sql` | `project` | MOM-0840 |
| `V20260716091000__create_signal_digests.sql` | `signal` | MOM-0840 |
| `V20260717090000__create_push_installations.sql` | `notification` | MOM-0840 |
| `V20260717090100__create_push_deliveries.sql` | `notification` | MOM-0840 |
| `V20260803090000__create_minsu_task_draft_generations.sql` | `minsu` | MOM-0840 |
| `V20260810090000__create_user_identities.sql` | `user` | MOM-0840 |
| `V20260811090000__add_minsu_generation_unfinished_index.sql` | `minsu` | MOM-0840 |

## 반영 중 — 0건

반영 PR이 열려 있고 아직 prod에 적용되지 않은 항목입니다. 릴리스 PR에서 차단됩니다.

없습니다.

## 반영 완료 — 1건

| 마이그레이션 | 모듈 | 근거 |
| --- | --- | --- |
| `V20260626023000__create_refresh_token.sql` | `auth` | momens-api#10 |

## 레거시 소유 미러 — 20건

레거시가 이미 소유한 스키마라 prod 반영 의무가 없습니다. 이 서버는 local/test용 미러만 만듭니다.

| 마이그레이션 | 모듈 | 근거 |
| --- | --- | --- |
| `V20260624090000__enable_extensions.sql` | `common` | - |
| `V20260624090100__create_user.sql` | `user` | - |
| `V20260624120000__add_user_job_role.sql` | `user` | - |
| `V20260627090000__create_workspace.sql` | `workspace` | - |
| `V20260627100000__create_workspace_label_sequences.sql` | `workspace` | - |
| `V20260703150000__create_project.sql` | `project` | - |
| `V20260706120000__create_task.sql` | `project` | - |
| `V20260707090000__create_source_refs_read_mirror.sql` | `source` | - |
| `V20260715100000__create_entity_relations_read_mirror.sql` | `context` | - |
| `V20260819090000__add_project_web_columns.sql` | `project` | - |
| `V20260819100000__create_milestone.sql` | `project` | - |
| `V20260819110000__create_memory_read_mirror.sql` | `memory` | - |
| `V20260820150313__create_blocker_read_mirror.sql` | `project` | - |
| `V20260821090000__add_task_web_read_columns.sql` | `project` | - |
| `V20260821090100__create_task_updates_read_mirror.sql` | `project` | - |
| `V20260821090200__add_source_ref_web_read_columns.sql` | `source` | - |
| `V20260821100000__create_source_connections_mirror.sql` | `source` | - |
| `V20260821100100__create_source_credentials_mirror.sql` | `source` | - |
| `V20260821100200__add_source_ref_full_read_columns.sql` | `source` | - |
| `V20260821140000__create_memory_write_mirror.sql` | `memory` | - |
<!-- END GENERATED: prod-schema -->

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
| MOM-0836 `users.email` UNIQUE 제거 | `required` | 서버 코드 배포가 먼저이고 제약 제거가 나중입니다. MOM-0836 완료 시 갱신합니다 |
| Google OAuth redirect URI 등록 | `확인 필요` | Kubernetes 값은 `https://api.momens.works/api/auth/google/callback`입니다. Google Cloud 콘솔 등록 상태는 저장소에서 확인할 수 없습니다 |
| 모바일·웹 client ID와 audiences 일치 | `확인 필요` | 실제 secret 값과 Google OAuth client ID 목록을 배포 전에 대조합니다 |
| FCM 프로젝트·ADC 자격증명 | `비활성` | push 기본값은 꺼져 있습니다. 활성화할 때 `MOMENS_NOTIFICATION_PUSH_FIREBASE_PROJECT_ID`와 ADC를 확인합니다 |
| Minsu GCP 프로젝트·리전·ADC | `비활성` | task draft와 비동기 enroll/drain 기본값은 꺼져 있습니다. 활성화 조건은 관련 설계 문서가 소유합니다 |
| source provider redirect URI 등록 | `확인 필요` | GitHub, Slack, Notion, Figma의 관리 화면에 `https://api.momens.works/api/source-connections/oauth/callback`을 등록해야 합니다. 신규 경로에는 레거시 경로에 없는 `/api` 접두사가 포함되어 있어 서로 다른 주소입니다. 기존 주소를 삭제하지 않고 신규 주소를 추가하면 redirect URI 설정만 변경해 레거시 경로로 되돌릴 수 있습니다. 등록 여부는 레포지토리에서 확인할 수 없습니다. |
| source provider 자격 증명과 토큰 키 주입 | `비활성` | 관련 설정의 기본값이 모두 비어 있어 현재는 어떤 provider도 설정되지 않은 상태입니다. 이 상태에서는 source 연결을 시작할 수 없지만 서버 기동에는 영향을 주지 않습니다. 활성화하려면 provider 네 곳의 Client ID와 secret, `MOMENS_SOURCE_OAUTH_REDIRECT_URI`, `MOMENS_SOURCE_OAUTH_TOKEN_KEY`를 주입해야 합니다. 토큰 키는 base64로 디코딩했을 때 32바이트여야 하며, `momens-worker`에서 사용하는 값과 같아야 합니다. |
| source OAuth state 서명 비밀 | `비활성` | 전환이 완료될 때까지 레거시의 인증 JWT 시크릿과 같은 값을 `MOMENS_SOURCE_OAUTH_STATE_SECRET`에 주입해야 합니다. 값이 다르면 한쪽 서버가 발급한 state를 다른 서버가 검증하지 못해 전환 중 source 연결이 실패합니다. 전환이 끝난 뒤에는 이 값을 독립적으로 교체할 수 있습니다. |
| DNS·ingress·TLS 인증서 | `응답 확인` | 2026-08-13에 `https://api.momens.works/api/health`의 TLS 응답(HTTP 401)을 확인했습니다. 애플리케이션 readiness를 뜻하지는 않습니다 |
