# Git

Git 워크플로는 GitFlow를 따르고, 커밋·브랜치·PR 형식은 아래로 통일합니다.

## 브랜치 전략

- `develop`: 기본 개발 브랜치
- `main`: 릴리즈·배포 브랜치
- 일반 PR 대상은 `develop`, 릴리즈 시 `develop` → `main`

## 브랜치 이름

`<Linear-이슈ID>-<타입>/<작업-내용>`

- 예: `MOM-15-feat/create-category`, `MOM-23-fix/category-not-found`
- 이슈는 Linear(`Momens-backend`, 키 `MOM`)에서 관리합니다. 브랜치 앞에 Linear 이슈
  ID(`MOM-15`)를 두면 Linear가 브랜치·PR을 해당 이슈에 자동 연결합니다.
- 브랜치 타입: `feat`, `fix`, `docs`, `refactor`, `chore`

## 커밋 메시지

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

## 이슈 / PR 제목

`[Feature] / [Bug] / [Refactor] / [Chore] / [Docs] <제목>`

- 예: `[Feature] 카테고리 생성 API 구현`
- PR 본문에 `Fixes MOM-15`처럼 Linear 이슈 ID를 적으면 머지 시 해당 이슈가 자동으로
  Done 처리됩니다.

## 리뷰

- PR은 머지 전 **최소 1명의 승인이 필요합니다**(`protected-branches` ruleset 강제, 작성자
  self-approve 불가). CODEOWNERS(`@Momens-Works/momens-backend`)에 리뷰가 자동 요청됩니다.
- `LGTM`만 남기지 않고, 확인한 범위·판단 근거를 짧게 남깁니다.

## 머지

- **rebase merge로 통일** — 머지 커밋 없이 선형 히스토리를 유지합니다.
- `develop`/`main`은 PR로만 변경(직접 push 차단).
- CI(`build`, `pr-format`) 통과 + 리뷰 대화 resolve 후 머지.
- 머지된 브랜치는 자동 삭제. force-push·브랜치 삭제 차단.
- 위 머지 규칙은 GitHub ruleset(`protected-branches`)으로 강제됩니다. 현재 ruleset은
  `main`/`develop`에 active 상태이며, rebase merge만 허용하고 필수 체크(`build`,
  `pr-format`)와 리뷰 대화 resolve를 요구합니다.

## 릴리즈 노트

- 릴리즈 노트의 기준은 GitHub Release입니다. 배포 워크플로와는 분리해 운영합니다.
- 릴리즈 PR은 `develop` → `main`으로 올리고, 머지 후 GitHub Release를 발행합니다.
- 최초 릴리즈 태그는 `v0.1.0`입니다.
- GitHub Release 발행 시 `Generate release notes`를 사용합니다. 릴리즈 노트 카테고리는
  `.github/release.yml`을 따릅니다.
- GitHub Release가 `published`되면 `release-notes-slack` 워크플로가
  `SLACK_RELEASE_WEBHOOK_URL` secret의 Incoming Webhook으로 릴리즈 노트를 Slack에 공유합니다.
