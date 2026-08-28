package works.momens.server.auth.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import works.momens.server.auth.internal.config.AuthProperties;

/** 보호 API의 Bearer 토큰 추출: 모바일 헤더 + 웹 access 쿠키 + 전환기 레거시 session_token 쿠키(ADR-0017). */
class CookieBearerTokenResolverTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final BearerTokenResolver resolver =
      new SecurityConfig()
          .bearerTokenResolver(properties(), new BearerTokenResolutionMetrics(meterRegistry));

  @Test
  @DisplayName("Authorization 헤더가 없으면 access_token 쿠키에서 토큰을 읽는다")
  void resolvesFromAccessCookieWhenNoAuthorizationHeader() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("access_token", "cookie-jwt"));

    assertThat(resolver.resolve(request)).isEqualTo("cookie-jwt");
  }

  @Test
  @DisplayName("Authorization 헤더가 access_token 쿠키보다 우선한다")
  void prefersAuthorizationHeaderOverAccessCookie() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer header-jwt");
    request.setCookies(new Cookie("access_token", "cookie-jwt"));

    assertThat(resolver.resolve(request)).isEqualTo("header-jwt");
  }

  @Test
  @DisplayName("헤더도 쿠키도 없으면 null을 반환한다")
  void returnsNullWhenNeitherHeaderNorCookiePresent() {
    assertThat(resolver.resolve(new MockHttpServletRequest())).isNull();
  }

  @Test
  @DisplayName("헤더와 access_token 쿠키가 없으면 레거시 session_token 쿠키에서 토큰을 읽는다")
  void resolvesFromLegacySessionCookieWhenNoHeaderOrAccessCookie() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("session_token", "legacy-jwt"));

    assertThat(resolver.resolve(request)).isEqualTo("legacy-jwt");
  }

  @Test
  @DisplayName("access_token 쿠키가 레거시 session_token 쿠키보다 우선한다")
  void prefersAccessCookieOverLegacySessionCookie() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("access_token", "cookie-jwt"), new Cookie("session_token", "legacy-jwt"));

    assertThat(resolver.resolve(request)).isEqualTo("cookie-jwt");
  }

  @Test
  @DisplayName("Authorization 헤더가 레거시 session_token 쿠키보다 우선한다")
  void prefersAuthorizationHeaderOverLegacySessionCookie() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer header-jwt");
    request.setCookies(new Cookie("session_token", "legacy-jwt"));

    assertThat(resolver.resolve(request)).isEqualTo("header-jwt");
  }

  @Test
  @DisplayName("해석 경로마다 mode 태그로 나뉜 카운터를 올린다")
  void countsResolutionsByMode() {
    MockHttpServletRequest legacy = new MockHttpServletRequest();
    legacy.setCookies(new Cookie("session_token", "legacy-jwt"));
    MockHttpServletRequest access = new MockHttpServletRequest();
    access.setCookies(new Cookie("access_token", "cookie-jwt"));
    MockHttpServletRequest header = new MockHttpServletRequest();
    header.addHeader("Authorization", "Bearer header-jwt");
    // 두 쿠키를 함께 가진 브라우저. 이 지표의 의미가 "레거시 쿠키 보유"가 아니라 "fallback에 실제로
    // 의존한 요청"이라는 것이 여기서 갈린다. access가 있으면 legacy 분기에 도달하지 않는다.
    MockHttpServletRequest both = new MockHttpServletRequest();
    both.setCookies(
        new Cookie("access_token", "cookie-jwt"), new Cookie("session_token", "legacy-jwt"));

    resolver.resolve(header);
    resolver.resolve(access);
    resolver.resolve(both);
    resolver.resolve(legacy);
    resolver.resolve(legacy);
    resolver.resolve(new MockHttpServletRequest());

    assertThat(count("header")).isEqualTo(1);
    assertThat(count("access_cookie")).isEqualTo(2);
    assertThat(count("legacy_session_cookie")).isEqualTo(2);
    assertThat(count("none")).isEqualTo(1);
  }

  @Test
  @DisplayName("첫 요청 전에도 mode 4개 시계열이 0으로 등록돼 있다")
  void registersEveryModeCounterAtZero() {
    assertThat(meterRegistry.find("momens.auth.bearer.token.resolutions").counters())
        .hasSize(4)
        .allSatisfy(counter -> assertThat(counter.count()).isZero());
  }

  private double count(String mode) {
    return meterRegistry
        .get("momens.auth.bearer.token.resolutions")
        .tag("mode", mode)
        .counter()
        .count();
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
