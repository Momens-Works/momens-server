package works.momens.server.source.connection.oauth;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * source 연동에 필요한 OAuth 설정입니다.
 *
 * <p>레거시는 {@code GITHUB_CLIENT_ID}처럼 접두사가 없는 환경변수를 사용하지만, 신규 서버는 모든 설정을 {@code momens} 아래에서 관리하므로
 * {@code momens.source.oauth}로 옮깁니다. {@code :auth} 모듈에서 레거시의 {@code GOOGLE_CLIENT_ID}를 {@code
 * MOMENS_AUTH_GOOGLE_CLIENT_ID}로 옮긴 방식과 같습니다. 설정 키는 달라지지만 값은 레거시와 같아야 합니다.
 *
 * <p>provider 자격 증명이 비어 있으면 해당 provider가 설정되지 않은 것으로 판정하며, source 연결을 시작할 수 없습니다. 레거시에서 provider
 * 활성화 여부를 판정하는 방식과 같습니다.
 */
@Validated
@ConfigurationProperties(prefix = "momens.source.oauth")
public record SourceOAuthProperties(
    String redirectUri,
    String successRedirectUri,
    String stateSecret,
    String tokenKey,
    @NotNull Duration stateTtl,
    @NotNull Map<String, ProviderCredentials> providers) {

  public record ProviderCredentials(String clientId, String clientSecret) {

    public boolean isConfigured() {
      return hasText(clientId) && hasText(clientSecret);
    }
  }

  public ProviderCredentials credentialsOf(String sourceType) {
    return providers.getOrDefault(
        sourceType.toLowerCase(Locale.ROOT), new ProviderCredentials(null, null));
  }

  public boolean hasAnyConfiguredProvider() {
    return hasText(redirectUri)
        && providers.values().stream().anyMatch(ProviderCredentials::isConfigured);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
