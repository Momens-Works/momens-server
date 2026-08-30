# Git

Git 워크플로는 GitFlow를 따르고, 커밋·브랜치·PR 형식은 아래로 통일합니다.

## 브랜치 전략

- `develop`: 기본 개발 브랜치
- `main`: 릴리즈·배포 브랜치
- 일반 PR 대상은 `develop`, 릴리즈 시 `develop` → `main`

## 브랜치 이름

`<Momens-작업-라벨>-<타입>/<작업-내용>`

- 예: `MOM-0680-feat/create-category`, `MOM-0681-fix/category-not-found`
- 작업은 Momens에서 관리합니다. 브랜치를 만들기 전에 관련 작업을 조회하고, 없으면 Momens MCP
  또는 웹에서 생성해 `MOM-0680` 형태의 라벨을 발급받습니다.
- 브랜치 앞에 Momens 작업 라벨을 두는 것은 추적을 위한 규칙입니다. 라벨만으로 Momens 작업과
  GitHub 브랜치·PR이 자동 연결되지는 않습니다.
- 브랜치 타입: `feat`, `fix`, `docs`, `refactor`, `chore`

## Momens 작업

- 코드·문서 작업은 브랜치를 만들기 전에 기존 Momens 작업을 먼저 조회합니다. 같은 범위의 작업이
  없으면 새 작업을 만들고, 중복 작업을 만들지 않습니다.
- 새 작업에는 최소한 제목, 배경·목적, 작업 범위, 완료 조건을 기록합니다. 프로젝트·마일스톤·담당자·
  우선순위는 확인된 값만 지정하고 추측하지 않습니다.
- 아직 착수하지 않은 작업은 `backlog` 또는 `todo`, 구현을 시작한 작업은 `in_progress`로 둡니다.
- Momens MCP를 사용할 수 있으면 조회·생성·상태 변경에 우선 사용합니다. 사용할 수 없으면 Momens
  웹에서 처리하며 다른 작업 관리 도구나 GitHub Issue를 대체 작업 원장으로 만들지 않습니다.
- AI 에이전트와 작업을 시작할 때는 `start-work` 스킬로 중복 조회·생성·`in_progress` 전환·브랜치
  생성을 한 번에 수행합니다.

## 커밋 메시지

형식: `<type>(<Momens-작업-라벨>[/<도메인>]): <메시지>`

- 도메인이 있으면 함께 작성합니다. 예: `feat(MOM-0680/category): 카테고리 생성 API 추가`
- 도메인이 없으면 Momens 작업 라벨만 작성합니다. 예: `docs(MOM-0812): README 문서 수정`
- 도메인은 소문자로 작성합니다.
- 커밋 제목에 Momens 작업 라벨을 포함하면 커밋 목록이나 blame에서 해당 변경이 어떤 작업에서
  이루어졌는지 확인할 수 있습니다.
- squash merge를 사용하지 않으므로 각 커밋이 개별 이력으로 남습니다. 따라서 각 커밋에는 실제로
  수행한 작업의 라벨을 작성합니다.
- 하나의 PR에서는 하나의 작업만 다루는 것이 원칙이므로, 일반적으로 PR에 포함된 모든 커밋에
  동일한 작업 라벨을 작성합니다. 다른 작업에 해당하는 변경이 함께 남아 있다면 각 커밋에 해당
  작업의 라벨을 작성합니다.
- Dependabot 등에서 자동으로 생성한 커밋에는 작업 라벨 규칙을 적용하지 않습니다.

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

## 작업 / PR 제목

`[Feature] / [Bug] / [Refactor] / [Chore] / [Docs] <제목>`

- 예: `[Feature] 카테고리 생성 API 구현`
- `Fixes MOM-0680`이나 브랜치 라벨만으로 Momens 작업이 자동 완료된다고 가정하지 않습니다.
- `pr-format` CI는 모든 PR의 제목·본문 형식을, 일반 PR은 추가로 브랜치(`MOM-<번호>-<타입>/…`) 형식을
  검사합니다. `develop` → `main` 릴리즈 PR은 브랜치 검사에서 제외합니다.

## 리뷰

- PR은 머지 전 **최소 1명의 승인이 필요합니다**(보호 ruleset 강제, 작성자
  self-approve 불가). 리뷰어는 PR을 열 때 직접 지정합니다(자동 요청 없음).
- `LGTM`만 남기지 않고, 확인한 범위·판단 근거를 짧게 남깁니다.

## 머지

- 일반 PR(`*` → `develop`)은 **rebase merge**합니다. `develop`은 머지 커밋 없는 선형 히스토리를
  유지합니다.
- 릴리즈 PR(`develop` → `main`)과 hotfix PR(`*` → `main`)은 **merge commit**으로 머지합니다.
  `main`에는 릴리즈·hotfix 경계인 머지 커밋을 남기고, `develop`의 기존 커밋 SHA를 다시 쓰지
  않습니다. `main` hotfix는 후속 PR로 `develop`에도 반영합니다.
- squash merge는 사용하지 않습니다.
- `develop`/`main`은 PR로만 변경(직접 push 차단).
- CI(`build`, `pr-format`) 통과 + 리뷰 대화 resolve 후 머지.
- 머지된 일반 작업 브랜치는 자동 삭제합니다. 기본 브랜치인 `develop`은 릴리즈 PR 머지 후에도
  유지합니다. force-push·보호 브랜치 삭제는 차단합니다.
- PR이 실제로 머지된 뒤 관련 Momens 작업을 `done`으로 변경합니다. PR을 열었거나 승인받은 시점에는
  완료 처리하지 않습니다. PR이 닫혔지만 머지되지 않았다면 `done`으로 변경하지 않고 실제 작업 상태에
  맞게 유지하거나 `cancelled`로 변경합니다.
- AI 에이전트와 완료 처리할 때는 `finish-work` 스킬로 GitHub의 실제 머지를 검증한 뒤 `done` 전환과
  머지 정보 댓글을 수행합니다.
- 위 머지 규칙은 GitHub ruleset으로 강제됩니다. `protected-develop`은 rebase merge와 선형
  히스토리만 허용하고, `protected-main`은 merge commit만 허용하며 선형 히스토리를 요구하지
  않습니다. 두 ruleset 모두 active 상태이며 필수 체크(`build`, `pr-format`), 승인 1명과 리뷰
  대화 resolve를 요구합니다. 리포 설정은 rebase merge와 merge commit을 활성화하고 squash
  merge를 비활성화하며, 브랜치별 허용 방식은 ruleset이 좁힙니다.

## 릴리즈 노트

- 릴리즈 노트의 기준은 GitHub Release입니다. 배포 워크플로와는 분리해 운영합니다.
- 릴리즈 PR은 `develop` → `main`으로 올리고, 머지 후 GitHub Release를 발행합니다.
- 최초 릴리즈 태그는 `v0.1.0`입니다.
- GitHub Release 발행 시 `Generate release notes`를 사용합니다. 릴리즈 노트 카테고리는
  `.github/release.yml`을 따릅니다.
- GitHub Release가 `published`되면 `release-notes-slack` 워크플로가
  `SLACK_RELEASE_WEBHOOK_URL` secret의 Incoming Webhook으로 릴리즈 노트를 Slack에 공유합니다.
