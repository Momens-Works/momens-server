package works.momens.server.auth.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import works.momens.server.auth.internal.validation.ValidJwtSecret;

/**
 * auth 모듈 설정(`momens.auth.*`).
 *
 * <p>access token은 단일 발급자(우리 서버)가 발급·검증하므로 대칭키 HS256을 씁니다. 짧은 access TTL은 변경된 권한의 stale 상한이 되므로(늦어도
 * TTL 안에는 반영) 운영에서 조정할 수 있게 설정으로 둡니다. refresh token은 서버 저장형으로 관리하며(ADR-0005), Google ID token은 별도
 * 검증 서비스가 aud/issuer/JWKS를 검증합니다(ADR-0004).
 */
@Validated
@ConfigurationProperties("momens.auth")
public record AuthProperties(
    @NotBlank @ValidJwtSecret String jwtSecret,
    @DurationMin(seconds = 1) Duration accessTtl,
    @DurationMin(seconds = 1) Duration refreshTtl,
    @Valid Google google) {

  public AuthProperties {
    if (accessTtl == null) {
      accessTtl = Duration.ofMinutes(15);
    }
    if (refreshTtl == null) {
      refreshTtl = Duration.ofDays(14);
    }
    if (google == null) {
      google = new Google(List.of(), Google.DEFAULT_JWK_SET_URI);
    }
  }

  public record Google(@NotEmpty List<@NotBlank String> audiences, @NotBlank String jwkSetUri) {

    static final String DEFAULT_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    public Google {
      audiences = audiences == null ? List.of() : List.copyOf(audiences);
      if (jwkSetUri == null) {
        jwkSetUri = DEFAULT_JWK_SET_URI;
      }
    }
  }
}
