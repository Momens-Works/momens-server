package works.momens.server.auth.internal.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import works.momens.server.auth.AuthErrorCode;
import works.momens.server.auth.internal.config.AuthProperties;
import works.momens.server.auth.internal.refresh.ClientType;
import works.momens.server.auth.internal.refresh.RefreshToken;
import works.momens.server.auth.internal.refresh.RefreshTokenStore;
import works.momens.server.common.api.BusinessException;

/** 우리 access token 발급/검증 round-trip과 거부 경로 단위 검증. */
class JwtTokenServiceTest {

  private static final String SECRET = "unit-test-momens-auth-jwt-secret-0123456789abcdef";
  private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
  private static final AuthProperties.Google GOOGLE =
      new AuthProperties.Google(
          List.of("test-google-client-id.apps.googleusercontent.com"),
          "https://www.googleapis.com/oauth2/v3/certs");

  private JwtTokenService service(String secret, Clock clock) {
    JwtConfig config = new JwtConfig(properties(secret));
    return new JwtTokenService(
        config.accessTokenEncoder(), properties(secret), clock, new InMemoryRefreshTokenStore());
  }

  @Test
  void ownDecoderAcceptsIssuedTokenWithUserIdSubject() {
    JwtConfig config = new JwtConfig(properties(SECRET));
    JwtTokenService service =
        new JwtTokenService(
            config.accessTokenEncoder(),
            properties(SECRET),
            Clock.systemUTC(),
            new InMemoryRefreshTokenStore());
    UUID userId = UUID.randomUUID();

    Jwt decoded = config.accessTokenDecoder().decode(service.issueAccessToken(userId));

    assertThat(decoded.getSubject()).isEqualTo(userId.toString());
    assertThat(decoded.getExpiresAt()).isAfter(decoded.getIssuedAt());
  }

  @Test
  void ownDecoderRejectsExpiredToken() {
    Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
    JwtConfig config = new JwtConfig(properties(SECRET));
    JwtTokenService service =
        new JwtTokenService(
            config.accessTokenEncoder(), properties(SECRET), past, new InMemoryRefreshTokenStore());
    String expired = service.issueAccessToken(UUID.randomUUID());

    assertThatThrownBy(() -> config.accessTokenDecoder().decode(expired))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void decoderWithDifferentSecretRejectsToken() {
    JwtTokenService issuer = service(SECRET, Clock.systemUTC());
    JwtConfig attacker =
        new JwtConfig(properties("different-momens-auth-jwt-secret-9876543210zyxwvu"));
    String token = issuer.issueAccessToken(UUID.randomUUID());

    assertThatThrownBy(() -> attacker.accessTokenDecoder().decode(token))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void refreshRotatesTokenAndRejectsReusedRefreshToken() {
    JwtConfig config = new JwtConfig(properties(SECRET));
    InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();
    JwtTokenService service =
        new JwtTokenService(
            config.accessTokenEncoder(), properties(SECRET), Clock.systemUTC(), store);
    UUID userId = UUID.randomUUID();
    TokenPair first = service.issueTokenPair(userId, ClientType.MOBILE, "iPhone");

    TokenPair rotated = service.refresh(first.refreshToken());

    assertThat(rotated.accessToken()).isNotBlank();
    assertThat(rotated.refreshToken()).isNotEqualTo(first.refreshToken());
    assertThatThrownBy(() -> service.refresh(first.refreshToken()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));
    assertThatThrownBy(() -> service.refresh(rotated.refreshToken()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));
  }

  private static AuthProperties properties(String secret) {
    return new AuthProperties(secret, ACCESS_TTL, Duration.ofDays(14), GOOGLE);
  }

  private static class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private final Map<String, RefreshToken> tokens = new HashMap<>();

    @Override
    public RefreshToken save(
        UUID userId, String tokenHash, ClientType clientType, String device, Instant expiresAt) {
      RefreshToken refreshToken =
          new RefreshToken(userId, tokenHash, clientType, device, expiresAt);
      tokens.put(tokenHash, refreshToken);
      return refreshToken;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
      return Optional.ofNullable(tokens.get(tokenHash));
    }

    @Override
    public void revoke(RefreshToken refreshToken, Instant revokedAt) {
      refreshToken.revoke(revokedAt);
    }

    @Override
    public void revokeActiveBySessionScope(
        UUID userId, ClientType clientType, String device, Instant revokedAt) {
      tokens.values().stream()
          .filter(token -> token.getUserId().equals(userId))
          .filter(token -> token.getClientType() == clientType)
          .filter(token -> java.util.Objects.equals(token.getDevice(), device))
          .filter(token -> token.isActive(revokedAt))
          .forEach(token -> token.revoke(revokedAt));
    }
  }
}
