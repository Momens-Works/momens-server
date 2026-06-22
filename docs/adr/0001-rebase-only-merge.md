# 0001. 머지 전략으로 rebase-only 채택

- 상태: Accepted
- 날짜: 2026-06-23

## 맥락

Git 워크플로는 이전 프로젝트(SOPT) 컨벤션을 베이스로 한다. 그 기본 머지 방식은 merge
commit(`Merge pull request #N …`)이다. 우리는 깔끔한 선형 히스토리를 원했고, 머지 방식을
GitHub 차원에서 강제하려 했다.

## 결정

머지는 rebase-only로 한다.

- repo 설정에서 merge commit·squash를 끄고 rebase만 허용한다.
- ruleset `protected-branches`에서 `main`/`develop`의 linear history와 rebase-only
  merge를 강제한다.
- 머지된 브랜치는 자동 삭제한다.

## 대안

- merge commit(SOPT 기본): 머지 커밋·분기를 보존 → 선형성 상실, 이력 노이즈.
- squash: 1 PR = 1 커밋 → 의미 단위 커밋 보존(커밋 컨벤션)과 충돌.

## 결과

- 좋음: 선형 히스토리, active ruleset으로 일관되게 강제.
- 감수: SOPT 기본과 다름(문서로 명시). rebase 충돌은 작성자가 해결.
