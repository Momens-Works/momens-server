package works.momens.server.source.internal.oauth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * {@link OAuthState}를 서명된 문자열로 변환하고 다시 복원합니다.
 *
 * <p>HS256으로 서명하며, 발급자({@code iss})는 {@code momens-api}, 대상({@code aud})은 {@code source-oauth},
 * 주체({@code sub})는 사용자 식별자입니다. 워크스페이스 식별자와 provider는 별도 claim에 저장하고, state 재사용을 막기 위한 난수 식별자도 함께
 * 포함합니다.
 *
 * <p><strong>발급자를 {@code momens-api}로 지정한 것은 의도된 동작입니다.</strong> 전환 기간에는 신규 서버가 발급한 state를 레거시가
 * 검증하거나 레거시가 발급한 state를 신규 서버가 검증하는 구간이 생길 수 있습니다. provider 관리 화면에 등록된 주소를 배포와 정확히 같은 시점에 변경할 수 없기
 * 때문입니다. 따라서 서명 비밀, 발급자, 대상, claim 이름을 레거시와 동일하게 유지합니다. 양쪽 서버가 서로 발급한 값을 검증할 수 있다는 것도 실제 실행으로
 * 확인했습니다.
 *
 * <p>만료 여부는 주입된 시각을 기준으로 판정하며 라이브러리의 기본 만료 검증은 비활성화합니다. 이를 통해 테스트에서 시각을 고정하고 만료 동작을 검증할 수 있습니다.
 *
 * <p>발급자 claim은 문자열로 읽습니다. 라이브러리의 표준 접근자는 발급자 값을 URL로 해석하지만, {@code momens-api}는 URL 형식이 아니므로 표준
 * 접근자를 사용하면 검증에 실패합니다.
 */
class OAuthStateSigner {

  static final String ISSUER = "momens-api";
  static final String AUDIENCE = "source-oauth";
  static final String ISSUER_CLAIM = "iss";
  static final String WORKSPACE_ID_CLAIM = "workspace_id";
  static final String PROVIDER_CLAIM = "provider";

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final int NONCE_BYTES = 16;

  private final NimbusJwtEncoder encoder;
  private final NimbusJwtDecoder decoder;
  private final Duration ttl;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  private OAuthStateSigner() {
    this.encoder = null;
    this.decoder = null;
    this.ttl = Duration.ZERO;
    this.clock = Clock.systemUTC();
  }

  static OAuthStateSigner unavailable() {
    return new OAuthStateSigner();
  }

  OAuthStateSigner(String secret, Duration ttl, Clock clock) {
    SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    this.decoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
    this.ttl = ttl;
    this.clock = clock;
  }

  String sign(OAuthState state) {
    requireConfigured();
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(state.userId().toString())
            .audience(List.of(AUDIENCE))
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .id(nonce())
            .claim(WORKSPACE_ID_CLAIM, state.workspaceId().toString())
            .claim(PROVIDER_CLAIM, state.provider())
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /**
   * 검증에 실패하면 예외를 던지지 않고 {@code null}을 반환합니다.
   *
   * <p>호출하는 쪽에서는 서명 불일치, 만료, 필수 claim 누락을 구분할 필요가 없습니다. 레거시도 이 경우를 하나의 오류로 처리합니다.
   */
  OAuthState verify(String token) {
    if (decoder == null) {
      return null;
    }
    Jwt jwt;
    try {
      jwt = decoder.decode(token);
    } catch (JwtException e) {
      return null;
    }
    if (!ISSUER.equals(jwt.getClaimAsString(ISSUER_CLAIM))) {
      return null;
    }
    if (jwt.getAudience() == null || !jwt.getAudience().contains(AUDIENCE)) {
      return null;
    }
    if (jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(clock.instant())) {
      return null;
    }
    UUID workspaceId = parseUuid(jwt.getClaimAsString(WORKSPACE_ID_CLAIM));
    UUID userId = parseUuid(jwt.getSubject());
    String provider = normalize(jwt.getClaimAsString(PROVIDER_CLAIM));
    if (workspaceId == null || userId == null || provider.isEmpty()) {
      return null;
    }
    return new OAuthState(workspaceId, userId, provider);
  }

  private void requireConfigured() {
    if (encoder == null) {
      throw new IllegalStateException("source oauth state secret is not configured");
    }
  }

  static String normalize(String provider) {
    return provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT);
  }

  private static UUID parseUuid(String value) {
    try {
      return value == null ? null : UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private String nonce() {
    byte[] bytes = new byte[NONCE_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
