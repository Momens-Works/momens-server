# 0004. 토큰 발급·검증 스택: Spring Security Resource Server + JOSE

- 상태: Accepted
- 날짜: 2026-06-25
- 작성자: Kimgyuilli

## 맥락

[ADR-0003](0003-auth-session-transport-model.md)의 하이브리드 세션 모델을 구현하려면 (1) 우리
access/refresh 토큰을 발급·검증하고, (2) 모바일 로그인에서 Google ID 토큰을 검증해야 한다. 어떤
라이브러리로 이 둘을 처리할지 결정해야 했다. `AGENTS.md`는 의존성 추가를 보수적으로 보라고 규정한다.

- 우리 토큰은 단일 발급자(우리 서버)가 발급·검증하므로 대칭키 HS256으로 충분하다.
- Google ID 토큰은 Google이 RS256 + 회전 공개키(JWKS)로 서명한다.
- `spring-boot-starter-security`는 이미 의존성에 있고, `/me`는 `Principal.getName()=userId`
  seam으로 준비돼 있다.

## 결정

**Spring Security OAuth2 Resource Server + JOSE(Nimbus)** 로 통일한다.

1. 우리 access 검증은 `oauth2ResourceServer().jwt()`로 **선언적으로** 처리한다(커스텀 인증
   필터를 만들지 않는다). 발급은 `NimbusJwtEncoder`(HS256), 검증은 전용 `NimbusJwtDecoder` 빈.
2. Google ID 토큰 검증은 Google JWKS 기반 `NimbusJwtDecoder` + issuer/audience validator를
   **자체 서비스로 캡슐화**하고 우리 access 디코더 빈과 분리한다(issuer·audience·key source가
   완전히 다름). resource-server가 잡는 `JwtDecoder` 빈과 충돌(모호성)시키지 않는다.
3. 웹 쿠키 전송(ADR-0003)은 `BearerTokenResolver` 교체 한 곳으로 확장한다.
4. 타사 토큰 라이브러리(JJWT, google-api-client)를 추가하지 않는다. Jackson 등 Spring Boot
   BOM이 관리하는 의존성만 명시한다.

## 대안

- **JJWT + google-api-client**: JJWT 빌더/파서는 가독성이 좋고 `GoogleIdTokenVerifier`는 ID
  토큰 검증의 정석이다. 그러나 타사 라이브러리 2개(각자 버전·CVE 관리)를 더하고, Bearer 인증
  필터·entry point·쿠키 추출·Authentication 배선을 직접 구현해 유지보수 표면이 커진다. Spring
  Security가 이미 제공하는 경로를 우회할 이유가 약해 기각.
- **커스텀 필터 + Nimbus 직접 사용**: 라이브러리는 줄지만 resource-server가 공짜로 주는 것을
  다시 만든다. 기각.

## 결과

- 타사 토큰 의존성 0. 버전·CVE 관리를 Spring Boot BOM에 위임한다.
- Bearer 인증·401 entry point·쿠키 seam을 Spring Security 표준 경로로 처리해 코드가 적다.
- `principal.getName()`이 `sub`(=userId)로 채워져 기존 `Principal.name=userId` seam과 글루
  없이 맞물린다.
- 인증/인가 거부 본문은 필터 단계라 전역 핸들러에 닿지 않으므로, `AuthenticationEntryPoint`/
  `AccessDeniedHandler`에서 공유 `ErrorResponse` shape로 직접 emit한다.
- Boot 4의 webmvc/json 스타터가 `jackson-databind`를 compile classpath로 노출하지 않아, 거부
  본문 직렬화용으로 `jackson-databind`(BOM 관리)를 명시 의존으로 둔다.
