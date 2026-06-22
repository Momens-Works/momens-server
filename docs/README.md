# momens-server 문서

`momens-server`의 문서 모음입니다. 사람이 읽는 문서는 한국어, AI/에이전트가 읽는
구조 문서는 영어로 작성합니다.

## 문서 구조

```text
docs/
├── README.md            # (이 문서) 문서 인덱스
├── ONBOARDING.md        # 신규 합류자 온보딩
├── CONVENTIONS.md       # 코드·Git·머지 컨벤션
├── LOCAL_DEVELOPMENT.md # 로컬 개발 환경
├── INITIAL_SETUP.md     # 초기 세팅 기록(히스토리)
├── spec/                # 서버 명세 (사람용)
└── ai/                  # AI/에이전트용 구조 문서 (영문)
```

규칙: `docs/ai/`만 AI용(영문)이고, 나머지 `docs/` 문서는 모두 사람용(한국어)입니다.
AI 진입점은 리포 루트의 [`AGENTS.md`](../AGENTS.md)입니다.

## 사람용 문서

- [온보딩](ONBOARDING.md) — 처음 합류했다면 여기서 시작
- [로컬 개발](LOCAL_DEVELOPMENT.md) — 로컬 실행/DB/프로필
- [컨벤션](CONVENTIONS.md) — 패키지·Spring·DTO·DB·테스트·Git·머지 정책
- [서버 명세](spec/README.md) — 서버 책임/도메인/API 명세
- [초기 세팅](INITIAL_SETUP.md) — 프로젝트 초기화 결정 기록

## AI용 문서

- [`AGENTS.md`](../AGENTS.md) — 에이전트 작업 가이드 (루트)
- [`ai/ARCHITECTURE.md`](ai/ARCHITECTURE.md) — modular modulith 구조/의존 방향

## 제품 레벨 문서는 어디에?

PRD·ADR·**용어집(glossary)**·product language 등 **제품 레벨 문서**는 이 리포가
아니라 [`teams`](https://github.com/Momens-Works/teams) 리포가 단일 출처입니다.
여기 서버 문서에서는 제품 용어를 복제하지 않고 `teams`를 링크/참조합니다.
