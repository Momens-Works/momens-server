package works.momens.server.auth.internal;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 우리 access token용 HS256 인코더/디코더.
 *
 * <p>우리 access decoder와 Google ID token decoder는 issuer·audience·key source가 완전히 달라 별도로 둡니다. Google
 * 검증은 {@code JwtDecoder} 빈으로 노출하지 않고 자체 서비스로 캡슐화해(Slice 2) resource-server 체인이 잡는 디코더와 충돌(빈 모호성)하지
 * 않게 합니다.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
class JwtConfig {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final SecretKey accessTokenKey;

  JwtConfig(AuthProperties properties) {
    this.accessTokenKey =
        new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
  }

  @Bean
  JwtEncoder accessTokenEncoder() {
    return new NimbusJwtEncoder(new ImmutableSecret<>(accessTokenKey));
  }

  @Bean
  JwtDecoder accessTokenDecoder() {
    return NimbusJwtDecoder.withSecretKey(accessTokenKey).macAlgorithm(MacAlgorithm.HS256).build();
  }

  /** 토큰 만료 계산용. 테스트에서 고정 Clock으로 교체해 만료 동작을 검증합니다. */
  @Bean
  Clock authClock() {
    return Clock.systemUTC();
  }
}
