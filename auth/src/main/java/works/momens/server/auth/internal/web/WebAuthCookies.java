package works.momens.server.auth.internal.web;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import works.momens.server.auth.internal.config.AuthProperties;

/**
 * 웹 인증 쿠키 빌더.
 *
 * <p>access는 모든 보호 API로 보내야 해 {@code Path=/}, refresh는 인증 엔드포인트로만 좁혀 {@code Path=/api/auth}로 둡니다.
 * 핸드셰이크용 state/PKCE-verifier는 콜백 경로로만 가는 단명 쿠키이고 콜백에서 즉시 만료합니다. same-domain 배포라 CSRF는 SameSite로
 * 충족합니다(MOM-22, ADR-0003).
 */
@Component
@RequiredArgsConstructor
public class WebAuthCookies {

  public static final String STATE_COOKIE = "oauth_state";
  public static final String PKCE_VERIFIER_COOKIE = "oauth_pkce_verifier";

  private static final Duration HANDSHAKE_TTL = Duration.ofMinutes(10);
  private static final String ACCESS_PATH = "/";
  private static final String AUTH_PATH = "/api/auth";

  private final AuthProperties properties;

  public ResponseCookie accessToken(String value) {
    return base(cookie().accessName(), value, ACCESS_PATH, properties.accessTtl()).build();
  }

  public ResponseCookie refreshToken(String value) {
    return base(cookie().refreshName(), value, AUTH_PATH, properties.refreshTtl()).build();
  }

  public ResponseCookie state(String value) {
    return base(STATE_COOKIE, value, AUTH_PATH, HANDSHAKE_TTL).build();
  }

  public ResponseCookie pkceVerifier(String value) {
    return base(PKCE_VERIFIER_COOKIE, value, AUTH_PATH, HANDSHAKE_TTL).build();
  }

  public ResponseCookie clearState() {
    return base(STATE_COOKIE, "", AUTH_PATH, Duration.ZERO).build();
  }

  public ResponseCookie clearPkceVerifier() {
    return base(PKCE_VERIFIER_COOKIE, "", AUTH_PATH, Duration.ZERO).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(
      String name, String value, String path, Duration maxAge) {
    AuthProperties.Web.Cookie cfg = cookie();
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(cfg.secure())
            .sameSite(cfg.sameSite())
            .path(path)
            .maxAge(maxAge);
    if (cfg.domain() != null && !cfg.domain().isBlank()) {
      builder.domain(cfg.domain());
    }
    return builder;
  }

  private AuthProperties.Web.Cookie cookie() {
    return properties.web().cookie();
  }
}
