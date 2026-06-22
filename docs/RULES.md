# 기반 규칙 (RULES)

프로젝트를 관통하는, 잘 바뀌지 않는 규칙을 모읍니다. 섹션 단위로 관리하고, 한 섹션이
정말 커지면 그때 별도 파일로 분리합니다.

## 원칙

- 확실하지 않은 규칙은 **추론하지 않고 합의로** 추가합니다. 미정 항목은
  [추후 결정 로그](DECISIONS-PENDING.md)에 둡니다.
- "왜 그렇게 정했나"의 근거는 [ADR](adr/)에 남기고, 여기에는 **상시 지시(무엇을 지킬지)**만
  둡니다.

> 규칙은 토픽별로 하나씩 채워집니다. 기존 [CONVENTIONS.md](CONVENTIONS.md)와
> [ai/ARCHITECTURE.md](ai/ARCHITECTURE.md)의 내용은 토픽 작업 시 이곳으로 이관 후
> 원본을 제거합니다.

## Git

Git 워크플로는 GitFlow를 따르고, 커밋·브랜치·PR 형식은 아래로 통일합니다.

### 브랜치 전략

- `develop`: 기본 개발 브랜치
- `main`: 릴리즈·배포 브랜치
- 일반 PR 대상은 `develop`, 릴리즈 시 `develop` → `main`

### 브랜치 이름

`<이슈번호>-<타입>/<작업-내용>`

- 예: `15-feat/create-category`, `23-fix/category-not-found`
- 브랜치 타입: `feat`, `fix`, `docs`, `refactor`, `chore`

### 커밋 메시지

형식: `<type> (<domain>): <메시지>`

- 예: `feat (Category): 카테고리 생성 API 구현`
- `(<domain>)`은 도메인이 있을 때 사용하고, 없으면 생략합니다(예: `docs: 문서 수정`).

| Type | 의미 |
| --- | --- |
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서 |
| `style` | 포맷·비동작 스타일 |
| `refactor` | 동작 보존 리팩터링 |
| `test` | 테스트 코드 |
| `chore` | 빌드·의존성·잡무 |
| `rename` | 파일/폴더 이름 변경·이동 전용 |
| `remove` | 파일 삭제 전용 |
| `!HOTFIX` | 긴급 critical 버그 수정 |

### 이슈 / PR 제목

`[Feature] / [Bug] / [Refactor] / [Chore] / [Docs] <제목>`

- 예: `[Feature] 카테고리 생성 API 구현`

### 리뷰

- PR은 최소 1명에게 리뷰 요청(권장). 승인은 강제하지 않습니다.
- `LGTM`만 남기지 않고, 확인한 범위·판단 근거를 짧게 남깁니다.

### 머지

- **rebase merge로 통일** — 머지 커밋 없이 선형 히스토리를 유지합니다.
- `develop`/`main`은 PR로만 변경(직접 push 차단).
- CI(`build`, `pr-format`) 통과 + 리뷰 대화 resolve 후 머지.
- 머지된 브랜치는 자동 삭제. force-push·브랜치 삭제 차단.
- 위 머지 규칙은 GitHub ruleset(`protected-branches`)으로 강제됩니다.
