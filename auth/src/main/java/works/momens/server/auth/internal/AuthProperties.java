package works.momens.server.auth.internal;

import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * auth 모듈 설정(`momens.auth.*`).
 *
 * <p>access token은 단일 발급자(우리 서버)가 발급·검증하므로 대칭키 HS256을 씁니다. 짧은 access TTL이 권한 변경 즉시 반영의 근거이므로 운영에서
 * 조정할 수 있게 설정으로 둡니다. refresh TTL·Google aud 목록은 모바일 로그인 슬라이스(Slice 2)에서 추가합니다.
 */
@Validated
@ConfigurationProperties("momens.auth")
public record AuthProperties(@NotBlank String jwtSecret, Duration accessTtl) {

  private static final int MIN_SECRET_BYTES = 32;

  public AuthProperties {
    if (accessTtl == null) {
      accessTtl = Duration.ofMinutes(15);
    }
    if (jwtSecret != null && jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "momens.auth.jwt-secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
    }
  }
}
