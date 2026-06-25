package works.momens.server.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

/** 우리 access token 발급/검증 round-trip과 거부 경로 단위 검증. */
class JwtTokenServiceTest {

  private static final String SECRET = "unit-test-momens-auth-jwt-secret-0123456789abcdef";
  private static final Duration ACCESS_TTL = Duration.ofMinutes(15);

  private JwtTokenService service(String secret, Clock clock) {
    JwtConfig config = new JwtConfig(new AuthProperties(secret, ACCESS_TTL));
    return new JwtTokenService(
        config.accessTokenEncoder(), new AuthProperties(secret, ACCESS_TTL), clock);
  }

  @Test
  void ownDecoderAcceptsIssuedTokenWithUserIdSubject() {
    JwtConfig config = new JwtConfig(new AuthProperties(SECRET, ACCESS_TTL));
    JwtTokenService service =
        new JwtTokenService(
            config.accessTokenEncoder(), new AuthProperties(SECRET, ACCESS_TTL), Clock.systemUTC());
    UUID userId = UUID.randomUUID();

    Jwt decoded = config.accessTokenDecoder().decode(service.issueAccessToken(userId));

    assertThat(decoded.getSubject()).isEqualTo(userId.toString());
    assertThat(decoded.getExpiresAt()).isAfter(decoded.getIssuedAt());
  }

  @Test
  void ownDecoderRejectsExpiredToken() {
    Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
    JwtConfig config = new JwtConfig(new AuthProperties(SECRET, ACCESS_TTL));
    JwtTokenService service =
        new JwtTokenService(
            config.accessTokenEncoder(), new AuthProperties(SECRET, ACCESS_TTL), past);
    String expired = service.issueAccessToken(UUID.randomUUID());

    assertThatThrownBy(() -> config.accessTokenDecoder().decode(expired))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void decoderWithDifferentSecretRejectsToken() {
    JwtTokenService issuer = service(SECRET, Clock.systemUTC());
    JwtConfig attacker =
        new JwtConfig(
            new AuthProperties("different-momens-auth-jwt-secret-9876543210zyxwvu", ACCESS_TTL));
    String token = issuer.issueAccessToken(UUID.randomUUID());

    assertThatThrownBy(() -> attacker.accessTokenDecoder().decode(token))
        .isInstanceOf(JwtException.class);
  }
}
