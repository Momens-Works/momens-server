# 온보딩

`momens-server`에 합류한 것을 환영합니다. 이 문서는 처음 합류한 서버 개발자가
**로컬에서 서버를 띄우고 첫 PR을 올리기까지** 필요한 것을 한 번에 안내합니다.

세부 규칙은 각 전용 문서로 링크합니다. 이 문서는 "흐름"을, 전용 문서는 "정확한
규칙"을 담습니다.

---

## 1. momens-server는 무엇인가

`momens-server`는 기존 Go/Gin 기반 `momens-api`를 대체하는 **새 Java Spring 제품 API
서버**입니다. 하나의 배포 가능한 Spring Boot 애플리케이션이며, 내부는 Gradle 멀티모듈
+ Spring Modulith로 경계를 두는 **modular modulith**를 지향합니다.

> 지금은 도메인 마이그레이션 전, 프로젝트 기반/협업 플로우를 다지는 단계입니다.

### 시스템 맥락

Momens는 여러 레포로 나뉘어 있습니다.

| 레포 | 역할 |
| --- | --- |
| [`teams`](https://github.com/Momens-Works/teams) | PRD·ADR·용어집·product language (제품 단일 출처) |
| `momens-api` | 레거시 Go/Gin API (대체 대상) |
| **`momens-server`** | **새 Java Spring 제품 API 서버 (여기)** |
| `momens-worker` | 외부 소스 수집·큐레이션 워커 |
| `momens-retrieval` | 검색 read-model 서버 (gRPC) |
| `k8s` | 쿠버네티스·인프라 정의 |

제품 차원의 배경/용어가 궁금하면 먼저 `teams`를 보세요.

---

## 2. 사전 준비물

- **JDK 21** (Temurin 권장)
- **Docker** (로컬 DB + 테스트의 Testcontainers에 필요 — 반드시 실행 중이어야 함)
- **Git**
- 권장 IDE: **IntelliJ IDEA** (`.editorconfig`/Spotless 자동 적용)

전역 Gradle은 필요 없습니다. 리포에 포함된 **Gradle Wrapper(`./gradlew`)** 를 씁니다.

---

## 3. 처음 한 번 세팅

```bash
# 1) 클론
git clone https://github.com/Momens-Works/momens-server.git
cd momens-server

# 2) 로컬 환경변수 준비 (.env 는 커밋하지 않음)
cp .env.example .env

# 3) 로컬 DB 기동 (pgvector PostgreSQL)
docker compose up -d

# 4) 빌드/테스트로 환경 검증 (Docker 실행 중이어야 함)
./gradlew test
```

`.env` 키 설명과 프로필 구성은 [로컬 개발](local-development.md)을 참고하세요.

---

## 4. 일상 개발 루프

```bash
# 애플리케이션 실행 (local 프로필)
./gradlew bootRun --args='--spring.profiles.active=local'

# 헬스체크 확인
curl http://localhost:8080/actuator/health   # {"status":"UP"}

# 코드 포맷 자동 적용 (커밋 전)
./gradlew spotlessApply

# 테스트
./gradlew test
```

- 포맷은 **Spotless + Google Java Format**. CI는 `spotlessCheck`로 검사하므로,
  커밋 전 `spotlessApply`를 습관화하세요.
- 테스트는 PostgreSQL **Testcontainers**를 사용합니다(H2 미사용). 그래서 **Docker가
  떠 있어야** 테스트가 통과합니다.

---

## 5. 프로젝트 구조

```text
momens-server
├── app/                       # 실행 애플리케이션 모듈 (Spring Boot main)
│   └── src/main/java/works/momens/server/MomensServerApplication.java
├── build.gradle               # 루트(공통 설정)
├── settings.gradle            # 멀티모듈 정의 (현재 :app)
├── docker-compose.yml         # 로컬 pgvector PostgreSQL
└── docs/                      # 문서 (이 폴더)
```

- 베이스 패키지: `works.momens.server`
- 실행 모듈은 `app`으로 확정. **도메인 모듈 목록은 추후 결정**합니다.
- 구조/의존 방향은 [기반 규칙 > 아키텍처](rules/architecture.md)에 정리되어 있습니다.

---

## 6. 협업 플로우

브랜치 전략은 **GitFlow**입니다. `develop`이 기본 개발 브랜치, `main`이 릴리즈
브랜치입니다. 일반 PR 대상은 `develop`입니다.

### 브랜치 이름

```text
<issue-number>-<type>/<work-description>
# 예) 15-feat/create-category
```

### 커밋 메시지

```text
feat (domain): 새로운 기능 추가
fix (domain): 버그 수정
docs: 문서 수정
```

타입과 형식 전체는 [기반 규칙 > Git](rules/git.md)을 참고하세요.

### PR

- 제목은 `[Feature] / [Bug] / [Refactor] / [Chore] / [Docs]` prefix로 시작합니다.
- PR 템플릿이 자동으로 채워집니다(요약·작업 유형·검증·리뷰 포인트 등).
- 열면 다음 체크가 돌고, **머지하려면 통과해야 합니다**:

| 체크 | 내용 | 머지 차단? |
| --- | --- | --- |
| `build` | `spotlessCheck` → `test` → `bootJar` | ✅ 필수 |
| `pr-format` | PR 제목/본문 형식 검증 | ✅ 필수 |
| CodeQL | 보안 정적 분석 | ℹ️ 비차단(Security 탭 알림) |

### 머지

- **rebase 머지로 통일**(머지 커밋 없음, 선형 히스토리).
- `develop`/`main`은 **PR로만** 변경(직접 push 차단), 리뷰 대화는 모두 resolve 후 머지.
- 머지된 브랜치는 자동 삭제됩니다.
- 현재 **승인(approval)은 필수가 아닙니다**. 단, CODEOWNERS(`@Momens-Works/momens-backend`)에
  리뷰 요청이 자동으로 갑니다 — 리뷰는 권장입니다.

머지 정책 전체는 [기반 규칙 > Git > 머지](rules/git.md#머지)에 있습니다.

---

## 7. 첫 기여 따라하기

```bash
# 1) 이슈 생성 ([Feature]/[Bug]/... 제목 prefix)
# 2) 작업 브랜치 생성
git switch develop && git pull
git switch -c 15-feat/create-category

# 3) 작업 → 포맷 → 커밋
./gradlew spotlessApply
git commit -m "feat (category): 카테고리 생성 API 추가"

# 4) 푸시 후 PR 생성 (base: develop)
git push -u origin 15-feat/create-category

# 5) CI(build, pr-format) 통과 + 리뷰 대화 resolve → rebase 머지
```

---

## 8. 문서 지도

| 문서 | 무엇 |
| --- | --- |
| [docs/README.md](README.md) | 문서 인덱스 |
| [local-development.md](local-development.md) | 로컬 실행·DB·프로필·`.env` |
| [기반 규칙](rules/README.md) | 코딩·Git·아키텍처 등 |
| [adr/](adr/) | 결정 기록 (ADR) |
| [pending-decisions.md](pending-decisions.md) | 추후 결정 로그 |
| [../AGENTS.md](../AGENTS.md) | AI 진입점 (Codex/Claude 공용) |

---

## 9. 막히면

- 로컬 실행/DB 문제 → [local-development.md](local-development.md)
- 규칙/컨벤션 질문 → [기반 규칙](rules/README.md)
- 제품 맥락/용어 → [`teams`](https://github.com/Momens-Works/teams)
- 그래도 막히면 백엔드 팀(`@Momens-Works/momens-backend`)에 물어보세요.
