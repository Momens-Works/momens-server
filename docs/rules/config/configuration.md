# 설정 (Configuration)

## 바인딩

- 설정은 타입 안전한 `@ConfigurationProperties`로 그룹 바인딩합니다. `@Value`는 일회성
  단순 값에만 사용합니다.
- `@ConfigurationProperties` 클래스는 record로 작성하고 `@Validated`로 검증합니다.

## 네임스페이스

- 애플리케이션 설정은 `momens.*` 아래 둡니다 (예: `momens.auth.*`, `momens.cors.*`).
- 프레임워크·서드파티 설정(`spring.*`, `management.*` 등)은 표준 키를 사용합니다.

## 프로필

- 프로필: `local`(기본) / `test` / `prod`. 활성화는 `SPRING_PROFILES_ACTIVE` 또는
  `--spring.profiles.active`.
- `application.yml`(공통) + `application-<profile>.yml`(환경별). 민감값은 env placeholder.

## 환경변수

- env → 설정은 Spring relaxed binding을 사용합니다
  (`momens.auth.jwt-secret` ← `MOMENS_AUTH_JWT_SECRET`).
- secret은 설정 파일에 두지 않고 env로 주입합니다([시크릿](secrets.md) 참고).

## 위치

- `@ConfigurationProperties` 클래스 위치는 모듈/패키지 구조([P1/P2](../../DECISIONS-PENDING.md))
  확정 후 정합니다. 지금은 `momens.*` 네임스페이스 원칙만 둡니다.
