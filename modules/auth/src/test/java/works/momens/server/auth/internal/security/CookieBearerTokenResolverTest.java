package works.momens.server.auth.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import works.momens.server.auth.internal.config.AuthProperties;

/** 보호 API의 Bearer 토큰 추출: 모바일 헤더 + 웹 access 쿠키 + 전환기 레거시 session_token 쿠키(ADR-0017). */
class CookieBearerTokenResolverTest {

  @Test
  void resolvesFromAccessCookieWhenNoAuthorizationHeader() {
    BearerTokenResolver resolver = new SecurityConfig().bearerTokenResolver(properties());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("access_token", "cookie-jwt"));

    assertThat(resolver.resolve(request)).isEqualTo("cookie-jwt");
  }

  @Test
  void prefersAuthorizationHeaderOverAccessCookie() {
    BearerTokenResolver resolver = new SecurityConfig().bearerTokenResolver(properties());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer header-jwt");
    request.setCookies(new Cookie("access_token", "cookie-jwt"));

    assertThat(resolver.resolve(request)).isEqualTo("header-jwt");
  }

  @Test
  void returnsNullWhenNeitherHeaderNorCookiePresent() {
    BearerTokenResolver resolver = new SecurityConfig().bearerTokenResolver(properties());

    assertThat(resolver.resolve(new MockHttpServletRequest())).isNull();
  }

  @Test
  void resolvesFromLegacySessionCookieWhenNoHeaderOrAccessCookie() {
    BearerTokenResolver resolver = new SecurityConfig().bearerTokenResolver(properties());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("session_token", "legacy-jwt"));

    assertThat(resolver.resolve(request)).isEqualTo("legacy-jwt");
  }

  @Test
  void prefersAccessCookieOverLegacySessionCookie() {
    BearerTokenResolver resolver = new SecurityConfig().bearerTokenResolver(properties());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("access_token", "cookie-jwt"), new Cookie("session_token", "legacy-jwt"));

    assertThat(resolver.resolve(request)).isEqualTo("cookie-jwt");
  }

  @Test
  void prefersAuthorizationHeaderOverLegacySessionCookie() {
    BearerTokenResolver resolver = new SecurityConfig().bearerTokenResolver(properties());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer header-jwt");
    request.setCookies(new Cookie("session_token", "legacy-jwt"));

    assertThat(resolver.resolve(request)).isEqualTo("header-jwt");
  }

  private static AuthProperties properties() {
    return new AuthProperties(
        "unit-test-momens-auth-jwt-secret-0123456789abcdef",
        Duration.ofMinutes(15),
        Duration.ofDays(14),
        new AuthProperties.Google(List.of("aud"), "https://www.googleapis.com/oauth2/v3/certs"),
        new AuthProperties.Web(
            null,
            new AuthProperties.Web.Cookie(true, "Lax", "access_token", "refresh_token", null),
            null));
  }
}
