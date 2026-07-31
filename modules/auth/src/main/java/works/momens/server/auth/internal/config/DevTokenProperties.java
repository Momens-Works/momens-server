package works.momens.server.auth.internal.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * dev 전용 토큰 발급 엔드포인트 설정(`momens.auth.dev-token.*`). {@link DevOnly} 프로필에서만 로드됩니다.
 *
 * <p>{@code secret}은 호출자를 제한하는 공유 시크릿으로, 요청 헤더 값과 상수시간으로 비교합니다. 시크릿 검증만 통과하면 JWT를 발급하므로, 배포할 때 너무
 * 짧은 시크릿이 들어가는 실수를 막으려고 최소 32자를 요구합니다(PR에서 권장한 난수 시크릿은 이보다 깁니다). {@code allowedEmails}는 토큰을 발급할 테스트
 * 사용자 allowlist입니다. 시크릿은 env로 주입하고 저장소에 커밋하지 않습니다(docs/rules/configuration.md). 값이 없으면 설정 검증에 걸리므로,
 * dev 설정 없이는 엔드포인트가 동작하지 않습니다.
 */
@Validated
@ConfigurationProperties("momens.auth.dev-token")
public record DevTokenProperties(
    @NotBlank @Size(min = 32) String secret,
    @NotEmpty List<@Email @NotBlank String> allowedEmails) {

  public DevTokenProperties {
    allowedEmails = allowedEmails == null ? List.of() : List.copyOf(allowedEmails);
  }
}
