package works.momens.server.source.internal.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * provider 한 곳의 OAuth 승인 URL 생성 규칙과 자격 증명을 포함합니다.
 *
 * <p>{@code authorizeUrl}이 생성하는 문자열은 레거시에서 생성하는 값과 완전히 같아야 합니다. 사용자가 이 URL에서 승인을 완료하면 provider가 사전에
 * 등록된 주소로 redirect하며, 요청 값이 다르면 provider가 승인을 거부할 수 있습니다.
 *
 * <p>쿼리 파라미터는 이름을 기준으로 정렬합니다. 레거시에서 사용하는 라이브러리가 같은 방식으로 정렬하므로 파라미터 순서까지 포함해 실제 실행 결과를 대조했습니다.
 * scope는 값이 있을 때만 포함합니다.
 */
record OAuthProvider(
    ProviderDefinition definition, String clientId, String clientSecret, String redirectUri) {

  String sourceType() {
    return definition.sourceType();
  }

  boolean isConfigured() {
    return hasText(clientId) && hasText(clientSecret) && hasText(redirectUri);
  }

  String authorizeUrl(String state) {
    Map<String, String> params = new TreeMap<>(definition.authorizeParams());
    params.put("client_id", clientId);
    params.put("redirect_uri", redirectUri);
    params.put("response_type", "code");
    params.put("state", state);
    if (!definition.scopes().isEmpty()) {
      params.put("scope", String.join(" ", definition.scopes()));
    }
    StringBuilder query = new StringBuilder();
    params.forEach(
        (name, value) -> {
          if (!query.isEmpty()) {
            query.append('&');
          }
          query.append(encode(name)).append('=').append(encode(value));
        });
    return definition.authorizeEndpoint() + "?" + query;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
