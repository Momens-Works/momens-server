# momens-server 문서

사람이 읽는 문서는 한국어로 작성합니다. AI/에이전트도 같은 문서를 읽습니다(별도 AI
문서를 두지 않습니다). AI 진입점은 리포 루트의 [`AGENTS.md`](../AGENTS.md)이고,
[`CLAUDE.md`](../CLAUDE.md)는 이를 가리키는 포인터입니다.

## 문서 구조

```text
docs/
├── README.md             # (이 문서) 인덱스
├── onboarding.md         # 신규 합류 가이드
├── local-development.md  # 로컬 개발
├── rules/                # 기반 규칙 (성격별 폴더)
├── adr/                  # 결정 기록 (ADR, 결정마다 파일 하나씩)
├── spec/                 # 서버 명세 (API/계약)
└── design/               # 상세설계 (현재 사실의 단일 출처)
```

문서 계층:

- **rules/** — 무엇을 지킬지(상시 규칙)
- **adr/** — 왜·언제 그렇게 정했는지(불변 기록)
- **spec/** — 서버 외부 계약과 명세(API 응답, 에러 코드 등)
- **design/** — 현재 사실의 단일 출처(상세설계). 필요해질 때 추가합니다.

## 문서 목록

- [온보딩](onboarding.md) — 처음 합류했다면 여기서 시작
- [로컬 개발](local-development.md)
- [기반 규칙](rules/README.md)
- [ADR](adr/README.md)
- [서버 명세](spec/README.md)
- [서버 명세 > 모바일 API](spec/mobile-api.md)
- [상세설계 > 모듈 맵](design/module-map.md)
- [상세설계 > 모바일 MVP 서버 요구사항](design/mobile-mvp-server-requirements.md)

## 제품 레벨 문서는 어디에?

PRD·ADR·**용어집(glossary)**·product language 등 **제품 레벨 문서**는 이 리포가 아니라
[`teams`](https://github.com/Momens-Works/teams) 리포가 단일 출처입니다. 서버 문서에서는
제품 용어를 복제하지 않고 `teams`를 링크/참조합니다. (이 리포에는 **서버 명세**만 둡니다.)
