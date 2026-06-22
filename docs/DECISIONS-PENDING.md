# 추후 결정 사항 (Pending Decisions)

아직 확정되지 않은 **열린 결정**을 모읍니다.

원칙:

- 추론으로 확정하지 않습니다. 대화와 확인을 통해 하나씩 결정합니다.
- 결정되면 해당 항목을 여기서 제거하고, **ADR**(결정 기록) 또는 **기반 규칙 문서**에
  반영합니다.
- 새로 생기는 열린 결정은 논의 중 바로 여기에 추가합니다.

| # | 주제 | 맥락 / 선택지 | 상태 |
| --- | --- | --- | --- |
| P1 | 도메인 모듈 구조 | 도메인 지향 vs 레이어 지향 / Gradle 서브모듈 vs 단일 모듈 패키지 + Modulith | 미정 |
| P2 | 도메인 모듈 목록·이름 | `workspace`/`product`/`memory`/`source`/`retrieval-integration` 등 | 미정 |
| P3 | Spring Boot 버전 라인 | 4.0.x 유지 vs 4.1.x 상향(상향 시 Modulith 2.1.x 동반) | 미정 |
| P4 | 첫 마이그레이션 수직 슬라이스 | 무엇부터? (auth / workspace 등) | 미정 |
| P5 | API 응답 포맷 | 마이그레이션 단계에서 결정, 우선 Go API 호환 유지 | 보류 |
| P6 | 인증/인가 상세 | Google OAuth, JWT, `SecurityFilterChain` 설계, `momens.auth.*` 키 | 구현 시점 |
| P7 | PR 승인 필수 여부 | 현재 0개. 추후 1개로 상향 검토 | 미정 |
| P8 | QueryDSL 도입 시점 | 쿼리 복잡도 필요 시 | 보류 |
| P9 | Dockerfile / 배포 / K8s | skeleton·CI 안정화 후 | 보류 |
| P10 | 문서 구조 재설계(안) | 이 문서 포함 전체 구조 — 본 대화에서 확정 예정 | 논의 중 |
| P11 | AI 진입점 파일 | `CLAUDE.md` 신설 vs 기존 `AGENTS.md` 유지/병행 | 미정 |
| P12 | 기반 규칙 문서 분할 방식 | `docs/rules/` 다중 파일 vs 단일 규칙 문서 | 미정 |

> 갱신 규칙: 항목이 결정되면 행을 제거하고 커밋 메시지/ADR에 근거를 남깁니다.
