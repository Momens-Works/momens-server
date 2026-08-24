package works.momens.server.source.connection.oauth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import works.momens.server.source.connection.oauth.ProviderDefinition.ClientAuthStyle;
import works.momens.server.source.connection.oauth.ProviderDefinition.IdentityMethod;
import works.momens.server.source.connection.oauth.ProviderDefinition.TokenBodyFormat;

/**
 * provider 이름에 해당하는 OAuth 승인 URL 생성 규칙을 조회합니다.
 *
 * <p>지원하는 provider는 GitHub, Slack, Notion, Figma이며 각각 승인 URL과 scope 규칙이 다릅니다. Slack은 여러 scope를 쉼표로
 * 연결해 별도 파라미터로 전달하고, Notion은 scope 대신 {@code owner}를 전달하며, Figma는 두 scope를 공백으로 연결합니다. 각 값과 조합 방식은
 * 레거시의 규칙을 따릅니다.
 *
 * <p>provider 이름을 비교할 때는 대소문자와 앞뒤 공백을 무시합니다. 웹에서 소문자 이름을 전달하기 때문입니다. 지원하지 않는 이름은 빈 값을 반환하며, 사용할 수
 * 없는 provider로 처리할지는 호출하는 쪽에서 결정합니다.
 */
class OAuthProviderRegistry {

  static final String GITHUB = "GITHUB";
  static final String SLACK = "SLACK";
  static final String NOTION = "NOTION";
  static final String FIGMA = "FIGMA";

  private static final Map<String, ProviderDefinition> DEFINITIONS =
      Map.of(
          GITHUB,
          new ProviderDefinition(
              GITHUB,
              "https://github.com/login/oauth/authorize",
              "https://github.com/login/oauth/access_token",
              List.of("read:user"),
              Map.of(),
              TokenBodyFormat.FORM,
              ClientAuthStyle.REQUEST_BODY,
              "https://api.github.com/user",
              IdentityMethod.GET,
              ProviderIdentities::github),
          SLACK,
          new ProviderDefinition(
              SLACK,
              "https://slack.com/oauth/v2/authorize",
              "https://slack.com/api/oauth.v2.access",
              List.of(),
              Map.of(
                  "scope",
                  "channels:read,channels:history,groups:read,groups:history,team:read,channels:join"),
              TokenBodyFormat.FORM,
              ClientAuthStyle.BASIC_HEADER,
              "https://slack.com/api/auth.test",
              IdentityMethod.POST,
              ProviderIdentities::slack),
          NOTION,
          new ProviderDefinition(
              NOTION,
              "https://api.notion.com/v1/oauth/authorize",
              "https://api.notion.com/v1/oauth/token",
              List.of(),
              Map.of("owner", "user"),
              TokenBodyFormat.JSON,
              ClientAuthStyle.BASIC_HEADER,
              null,
              IdentityMethod.NONE,
              ProviderIdentities::notion),
          FIGMA,
          new ProviderDefinition(
              FIGMA,
              "https://www.figma.com/oauth",
              "https://api.figma.com/v1/oauth/token",
              List.of("file_comments:read", "webhooks:write"),
              Map.of(),
              TokenBodyFormat.FORM,
              ClientAuthStyle.BASIC_HEADER,
              null,
              IdentityMethod.NONE,
              ProviderIdentities::figma));

  private final SourceOAuthProperties properties;

  OAuthProviderRegistry(SourceOAuthProperties properties) {
    this.properties = properties;
  }

  Optional<OAuthProvider> find(String sourceType) {
    String normalized = OAuthStateSigner.normalize(sourceType);
    ProviderDefinition definition = DEFINITIONS.get(normalized);
    if (definition == null) {
      return Optional.empty();
    }
    SourceOAuthProperties.ProviderCredentials credentials = properties.credentialsOf(normalized);
    return Optional.of(
        new OAuthProvider(
            definition,
            credentials.clientId(),
            credentials.clientSecret(),
            properties.redirectUri()));
  }
}
