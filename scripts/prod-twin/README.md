# prod 쌍둥이

MOM-0909 부트스트랩의 배포 리허설 환경입니다. prod 와 같은 형상의 PostgreSQL 을 로컬에 세우고
실제 시나리오와 실패 시나리오를 돌립니다.

## 왜 있는가

2026-08-23 리허설은 `scripts/legacy-diff` 의 `legacy-db` 에서 돌았습니다. 그 DB 는 **pg16 ·
superuser · 빈 DB · 무트래픽** 이라 prod 와 네 가지가 동시에 달랐고, 그래서 구조적으로 재현할 수
없는 축이 남았습니다(설계 문서 8절 "아직 리허설로 닫지 못한 것"). 이 디렉터리가 그 축을 닫습니다.

| 축 | legacy-db | 쌍둥이 |
| --- | --- | --- |
| PostgreSQL | 16 | **17** (prod 는 Supabase 17.6) |
| 앱 접속 role | superuser | **`momens_server`** — 비-superuser, 무소유 |
| 운영 창구 role | superuser | **비-superuser** — Supabase 의 `postgres` 는 superuser 가 아니다 |
| Supabase role | 없음 | **`anon`·`authenticated`·`service_role`** + ALTER DEFAULT PRIVILEGES |
| `tasks` 소유권 | 접속 role 이 소유 | **`postgres` 소유** — 이전이 선행 조건 |
| 데이터 | 없음 | **`tasks` 10 만 행** |
| 락 경합 | 없음 | **시나리오가 ACCESS EXCLUSIVE 를 건다** |
| 확장 스키마 | `public` | **`extensions`** — Supabase 가 `uuid-ossp` 를 두는 곳 |
| event trigger | 없음 | **6 개** — `supabase_admin` 소유, 매 DDL 마다 발화 |

운영 창구를 비-superuser 로 두는 것이 특히 중요합니다. superuser 는 `ALTER TABLE ... OWNER TO` 의
`SET ROLE` 검사를 통째로 건너뛰므로, superuser 로 리허설하면 prod 에서만 터지는 실패를 놓칩니다.

이 쌍둥이는 direct 접속을 전제합니다. prod DB 포트가 `5432` 로 확인돼(2026-08-26) 세션이
유지되므로 그 전제가 맞습니다. 접속 정보를 교체할 때 트랜잭션 pooler(`:6543`) 주소로 바뀌면
Flyway 의 세션 단위 잠금과 `init-sqls` 가 성립하지 않아 여기서 얻은 락 결과가 무효가 됩니다.

## 쓰는 법

`momens-api` 가 `momens-server` 와 같은 부모 디렉터리에 있어야 합니다(레거시 마이그레이션을
읽습니다).

```bash
./gradlew :app:bootJar
scripts/prod-twin/build.sh          # 쌍둥이 구축 → twin_base
scripts/prod-twin/rehearse.sh       # 전 시나리오
scripts/prod-twin/rehearse.sh lock  # 하나만
```

`build.sh` 는 `twin_base` 를 만들고, `rehearse.sh` 는 시나리오마다 그것을 `TEMPLATE` 으로 복제해
매번 같은 출발점에서 시작합니다.

정리는 `docker rm -f momens-prod-twin` 입니다.

## 시나리오

| 이름 | 무엇을 보는가 |
| --- | --- |
| `baseline` | 정상 경로. 선행 조건 → 심기 28행 → 부트스트랩 → `validate` 기동 → 토글 없이 재기동 |
| `no-ownership` | `tasks` 소유권 이전을 생략하면 |
| `no-references` | `users` 에 `REFERENCES` 를 주지 않으면 |
| `no-set-option` | 창구가 `momens_server` 로 `SET ROLE` 할 수 없으면 |
| `no-search-path` | `extensions` 스키마가 `momens_server` 에게 안 보이면 |
| `bulk-ownership` | 레거시 테이블 20개를 한 번에 넘기면 (ADR-0019 최종 상태) |
| `ownership-reverted` | 부트스트랩 성공 후 `tasks` 소유권을 되돌리면 |
| `history-grant` | 이력 테이블 권한 — 없을 때 / 소유권 이전일 때 / DML 부여일 때 |
| `lock` | 레거시가 `tasks` 를 ACCESS EXCLUSIVE 로 잡고 있으면 |
| `checksum` | 심은 체크섬 하나가 파일과 다르면 |

시나리오의 절반은 **실패해야 정상**입니다. 그래서 종료 코드가 아니라 기대한 문구가 나왔는지로
판정합니다 — 의도한 이유로 실패했는지를 가려야 하기 때문입니다.

## 결과

설계 문서 [`docs/design/prod-schema-ownership-transfer.md`](../../docs/design/prod-schema-ownership-transfer.md)
8절에 있습니다.

## 파일

| 파일 | 내용 |
| --- | --- |
| `build.sh` | 컨테이너 · 레거시 마이그레이션 · role · Supabase 형상 · 데이터 |
| `roles.sql` | prod 에서 실측한 role 형상 |
| `supabase-shape.sql` | 확장 스키마 분리와 event trigger. 레거시 마이그레이션이 재현하지 않는 것 |
| `data.sql` | 합성 데이터. prod 덤프를 가져오지 않습니다 |
| `rehearse.sh` | 시나리오 |
