# prod 스키마 반영 대장

<!--
이 파일은 scripts/prod-schema-ledger.sh --write 가 생성합니다. 직접 수정하지 마세요.
정본은 각 마이그레이션 첫 줄의 `-- prod-schema:` 헤더입니다.
-->

prod는 레거시 `momens-api`와 공유 DB를 쓰는 전환기라 이 서버의 Flyway가 꺼져 있고
`ddl-auto: validate`로 매핑만 검증합니다([데이터](rules/persistence.md)). 따라서 서버가 추가한
신규 스키마는 별도 `momens-api` 마이그레이션으로 prod에 반영해야 하고, 반영되지 않으면 매핑
검증에 실패해 **애플리케이션이 기동하지 않습니다.**

이 문서는 그 반영 상태를 마이그레이션 단위로 추적합니다.

## 미반영 — 16건

prod에 반영해야 하고 아직 `momens-api` PR이 없는 항목입니다. 릴리스 PR에서 차단됩니다.

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

`momens-api` PR이 열려 있고 아직 prod에 적용되지 않은 항목입니다. 릴리스 PR에서 차단됩니다.

없습니다.

## 반영 완료 — 1건

| 마이그레이션 | 모듈 | 근거 |
| --- | --- | --- |
| `V20260626023000__create_refresh_token.sql` | `auth` | momens-api#10 |

## 레거시 소유 미러 — 8건

레거시가 이미 소유한 스키마라 prod 반영 의무가 없습니다. 이 서버는 local/test용 미러만 만듭니다.

| 마이그레이션 | 모듈 | 근거 |
| --- | --- | --- |
| `V20260624090100__create_user.sql` | `user` | - |
| `V20260624120000__add_user_job_role.sql` | `user` | - |
| `V20260627090000__create_workspace.sql` | `workspace` | - |
| `V20260627100000__create_workspace_label_sequences.sql` | `workspace` | - |
| `V20260703150000__create_project.sql` | `project` | - |
| `V20260706120000__create_task.sql` | `project` | - |
| `V20260707090000__create_source_refs_read_mirror.sql` | `source` | - |
| `V20260715100000__create_entity_relations_read_mirror.sql` | `context` | - |
