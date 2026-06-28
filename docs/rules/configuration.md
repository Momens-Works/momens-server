# 설정 · 시크릿

## 설정 (Configuration)

### 바인딩

- 설정은 타입 안전한 `@ConfigurationProperties`로 그룹 바인딩합니다. `@Value`는 일회성
  단순 값에만 사용합니다.
- `@ConfigurationProperties` 클래스는 record로 작성하고 `@Validated`로 검증합니다.

### 네임스페이스

- 애플리케이션 설정은 `momens.*` 아래 둡니다 (예: `momens.auth.*`, `momens.cors.*`).
- 프레임워크·서드파티 설정(`spring.*`, `management.*` 등)은 표준 키를 사용합니다.

### 프로필

- 프로필: `local` / `test` / `prod`. 활성화는 `SPRING_PROFILES_ACTIVE` 또는
  `--spring.profiles.active`.
- `application.yml`(공통) + `application-<profile>.yml`(환경별). 민감값은 env placeholder.

### 환경변수

- env → 설정은 Spring relaxed binding을 사용합니다
  (`momens.auth.jwt-secret` ← `MOMENS_AUTH_JWT_SECRET`).
- secret은 설정 파일에 두지 않고 env로 주입합니다([시크릿](#시크릿) 참고).

### 위치

- `@ConfigurationProperties` 클래스는 관련 기능 모듈 안에 둡니다(모듈 구조는
  [ADR-0002](../adr/0002-gradle-multi-module-boundaries.md)). 모듈 내 구체 경로는 해당 모듈을
  만들며 정하고, 지금은 `momens.*` 네임스페이스 원칙만 둡니다.

## 시크릿

- 실제 secret은 커밋하지 않습니다. 커밋 가능한 설정 파일에는 환경변수 placeholder만 둡니다.
- 커밋 금지: `.env`, `.env.*`, `application-secret.yml`, `application-*-secret.yml`.
  커밋 가능: `application.yml`, `application-{local,test,prod}.yml`, `.env.example`.
- secret 주입: 로컬은 `.env`, CI는 GitHub Actions Secrets, 운영은 Kubernetes Secret 또는
  External Secrets.
- 개인 DM·개인 메모·private submodule·private repo를 secret 저장소로 쓰지 않습니다.
- 자세한 운영은 [로컬 개발](../local-development.md)을 참고합니다.
