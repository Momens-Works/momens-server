package works.momens.server.auth.internal.security;

import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import works.momens.server.auth.internal.config.AuthProperties;

/**
 * 전역 SecurityFilterChain. 코드 소유권은 auth 모듈에 둡니다(docs/design/module-map.md).
 *
 * <p>두 개의 체인으로 나눕니다. 공개 경로(인증 엔드포인트·health·swagger)는 자원서버 필터를 태우지 않는 별도 체인으로 처리합니다. 그렇지 않으면 클라이언트가
 * 만료된 access token을 {@code Authorization} 헤더에 그대로 붙여 보낼 때(예: 토큰 갱신 직전 refresh 호출) 자원서버 필터가
 * permitAll 평가 전에 401을 내버려 갱신 흐름이 막힙니다. 보호 API 체인은 우리 HS256 디코더로 access를 검증하고, 거부 본문은 Standard 에러
 * shape로 통일합니다. 웹 쿠키 인증(MOM-22)은 {@link BearerTokenResolver}가 access 쿠키도 읽도록 교체해 보호 체인 위에 얹습니다.
 * same-domain 배포라 CSRF는 SameSite 쿠키 속성으로 막습니다(ADR-0003).
 */
@Configuration
class SecurityConfig {

  private static final String[] PUBLIC_PATHS = {
    "/actuator/health/**",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/api/auth/google/token",
    "/api/auth/google/login",
    "/api/auth/google/callback",
    "/api/auth/refresh",
    "/api/auth/logout",
    "/api/auth/web/refresh",
    "/api/auth/web/logout"
  };

  /**
   * 공개 경로 전용 체인. 자원서버를 얹지 않으므로 stale Bearer 헤더가 와도 401 없이 컨트롤러까지 도달합니다. 인증 엔드포인트는 바디 자격증명(idToken/
   * refreshToken)으로 동작하고 거부는 컨트롤러의 도메인 에러로 처리합니다.
   */
  @Bean
  @Order(1)
  SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher(PUBLIC_PATHS)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      JwtDecoder accessTokenDecoder,
      BearerTokenResolver bearerTokenResolver,
      AuthenticationEntryPoint authenticationEntryPoint,
      AccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // 무상태 Bearer/쿠키 API. same-domain 배포라 CSRF는 SameSite 쿠키 속성으로 막습니다(ADR-0003).
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                    .bearerTokenResolver(bearerTokenResolver)
                    .jwt(jwt -> jwt.decoder(accessTokenDecoder)))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }

  /**
   * Bearer 토큰 추출. 모바일은 Authorization 헤더, 웹은 access HttpOnly 쿠키로 보냅니다. 헤더를 먼저 보고 없으면 access 쿠키에서
   * 읽습니다. 쿠키 이름은 설정({@code momens.auth.web.cookie.access-name})을 따릅니다.
   */
  @Bean
  BearerTokenResolver bearerTokenResolver(AuthProperties properties) {
    String accessCookieName = properties.web().cookie().accessName();
    DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
    return request -> {
      String fromHeader = headerResolver.resolve(request);
      if (fromHeader != null) {
        return fromHeader;
      }
      if (request.getCookies() != null) {
        for (Cookie cookie : request.getCookies()) {
          if (accessCookieName.equals(cookie.getName())) {
            return cookie.getValue();
          }
        }
      }
      return null;
    };
  }
}
