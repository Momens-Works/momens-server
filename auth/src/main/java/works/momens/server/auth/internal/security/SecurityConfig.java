package works.momens.server.auth.internal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 전역 SecurityFilterChain. 코드 소유권은 auth 모듈에 둡니다(docs/design/module-map.md).
 *
 * <p>무상태 Bearer 자원서버 체인입니다. access token 검증은 우리 HS256 디코더로 처리하고, 인증/인가 거부 본문은 Standard 에러 shape로
 * 통일합니다. 웹 쿠키 인증(MOM-22)은 {@link BearerTokenResolver} 교체와 CSRF 재활성화만으로 이 체인 위에 얹습니다.
 */
@Configuration
class SecurityConfig {

  private static final String[] PUBLIC_PATHS = {
    "/actuator/health/**",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/api/auth/google/token",
    "/api/auth/refresh",
    "/api/auth/logout"
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtDecoder accessTokenDecoder,
      BearerTokenResolver bearerTokenResolver,
      AuthenticationEntryPoint authenticationEntryPoint,
      AccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // 무상태 Bearer API. 웹 쿠키 인증(MOM-22) 도입 시 쿠키 요청에 한해 CSRF를 재활성화합니다.
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated())
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
   * Bearer 토큰 추출 seam. 현재는 Authorization 헤더만 읽습니다(기본 동작). 웹 쿠키 인증(MOM-22)은 여기서 HttpOnly 쿠키 분기를 한 곳에
   * 추가합니다.
   */
  @Bean
  BearerTokenResolver bearerTokenResolver() {
    return new DefaultBearerTokenResolver();
  }
}
