package works.momens.server.auth.internal.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * dev 전용 토큰 발급 엔드포인트 설정(`momens.auth.dev-token.*`). {@link DevOnly} 프로필에서만 로드됩니다.
 *
 * <p>{@code secret}은 호출자 제한용 공유 시크릿으로 요청 헤더 값과 상수시간 비교합니다. {@code allowedEmails}는 토큰을 발급할 테스트 사용자
 * allowlist입니다. 시크릿은 env로 주입하고 저장소에 커밋하지 않습니다(docs/rules/configuration.md). 값이 없으면 검증이 실패해 기동이 막히므로
 * dev 설정 없이는 엔드포인트가 동작하지 않습니다.
 */
@Validated
@ConfigurationProperties("momens.auth.dev-token")
public record DevTokenProperties(
    @NotBlank String secret, @NotEmpty List<@Email @NotBlank String> allowedEmails) {

  public DevTokenProperties {
    allowedEmails = allowedEmails == null ? List.of() : List.copyOf(allowedEmails);
  }
}
