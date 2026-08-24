package works.momens.server.source.connection.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import works.momens.server.source.connection.oauth.SourceOAuthProperties.ProviderCredentials;

/**
 * provider별 OAuth 승인 URL 생성 규칙이 레거시와 같은지 검증합니다.
 *
 * <p>기대값은 레거시 코드를 실제로 실행해 얻은 URL입니다. 파라미터 순서와 인코딩까지 동일해야 provider가 요청을 정상적으로 처리할 수 있습니다.
 */
class OAuthProviderRegistryTest {

  private static final String REDIRECT_URI =
      "https://api.momens.works/source-connections/oauth/callback";

  private final OAuthProviderRegistry registry =
      new OAuthProviderRegistry(
          new SourceOAuthProperties(
              REDIRECT_URI,
              "https://app.momens.works/settings/sources",
              "state-secret",
              null,
              Duration.ofMinutes(10),
              Map.of(
                  "github", new ProviderCredentials("cid-github", "sec"),
                  "slack", new ProviderCredentials("cid-slack", "sec"),
                  "notion", new ProviderCredentials("cid-notion", "sec"),
                  "figma", new ProviderCredentials("cid-figma", "sec"))));

  private String authorizeUrl(String sourceType) {
    return registry.find(sourceType).orElseThrow().authorizeUrl("STATE123");
  }

  @Test
  @DisplayName("GitHub 승인 URL을 레거시와 동일하게 생성한다")
  void buildsGithubAuthorizeUrlAsLegacyDoes() {
    assertThat(authorizeUrl("github"))
        .isEqualTo(
            "https://github.com/login/oauth/authorize?client_id=cid-github"
                + "&redirect_uri=https%3A%2F%2Fapi.momens.works%2Fsource-connections%2Foauth%2Fcallback"
                + "&response_type=code&scope=read%3Auser&state=STATE123");
  }

  @Test
  @DisplayName("Slack 승인 URL을 레거시와 동일하게 생성한다")
  void buildsSlackAuthorizeUrlAsLegacyDoes() {
    assertThat(authorizeUrl("slack"))
        .isEqualTo(
            "https://slack.com/oauth/v2/authorize?client_id=cid-slack"
                + "&redirect_uri=https%3A%2F%2Fapi.momens.works%2Fsource-connections%2Foauth%2Fcallback"
                + "&response_type=code"
                + "&scope=channels%3Aread%2Cchannels%3Ahistory%2Cgroups%3Aread%2Cgroups%3Ahistory"
                + "%2Cteam%3Aread%2Cchannels%3Ajoin&state=STATE123");
  }

  @Test
  @DisplayName("Notion 승인 URL을 레거시와 동일하게 생성한다")
  void buildsNotionAuthorizeUrlAsLegacyDoes() {
    assertThat(authorizeUrl("notion"))
        .isEqualTo(
            "https://api.notion.com/v1/oauth/authorize?client_id=cid-notion&owner=user"
                + "&redirect_uri=https%3A%2F%2Fapi.momens.works%2Fsource-connections%2Foauth%2Fcallback"
                + "&response_type=code&state=STATE123");
  }

  @Test
  @DisplayName("Figma 승인 URL을 레거시와 동일하게 생성한다")
  void buildsFigmaAuthorizeUrlAsLegacyDoes() {
    assertThat(authorizeUrl("figma"))
        .isEqualTo(
            "https://www.figma.com/oauth?client_id=cid-figma"
                + "&redirect_uri=https%3A%2F%2Fapi.momens.works%2Fsource-connections%2Foauth%2Fcallback"
                + "&response_type=code&scope=file_comments%3Aread+webhooks%3Awrite&state=STATE123");
  }

  @Test
  @DisplayName("지원하지 않는 provider 이름은 찾지 못한다")
  void hasNoProviderForAnUnknownSourceType() {
    assertThat(registry.find("linear")).isEmpty();
  }

  @Test
  @DisplayName("자격 증명이 없는 provider는 설정되지 않은 상태로 판정한다")
  void reportsProviderWithoutClientIdAsNotConfigured() {
    OAuthProviderRegistry withoutCredentials =
        new OAuthProviderRegistry(
            new SourceOAuthProperties(
                REDIRECT_URI, null, "state-secret", null, Duration.ofMinutes(10), Map.of()));

    assertThat(withoutCredentials.find("github").orElseThrow().isConfigured()).isFalse();
  }
}
