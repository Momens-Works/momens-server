package works.momens.server.source.internal.oauth;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * provider별 응답에서 외부 계정 정보를 추출하는 함수를 모아 둡니다.
 *
 * <p>각 provider 응답을 해석하는 방식은 레거시의 구현을 따릅니다.
 *
 * <p>GitHub는 사용자 정보 응답의 로그인 ID를 외부 계정 식별자로 사용하며, 이름이 없으면 로그인 ID로 대체합니다. Slack은 응답이 성공 상태가 아니거나 팀
 * 식별자가 없으면 유효하지 않은 응답으로 처리합니다. Notion과 Figma는 사용자 정보를 별도로 조회하지 않고 토큰 응답에 포함된 값을 사용합니다.
 */
final class ProviderIdentities {

  private ProviderIdentities() {}

  static ProviderIdentity github(
      Map<String, Object> tokenResponse, Map<String, Object> identityBody) {
    String login = string(identityBody, "login");
    if (login.isEmpty()) {
      throw new IllegalStateException("github identity missing login");
    }
    String name = string(identityBody, "name");
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("github_user_id", identityBody.get("id"));
    metadata.put("html_url", string(identityBody, "html_url"));
    metadata.put("login", login);
    return new ProviderIdentity(login, name.isEmpty() ? login : name, metadata);
  }

  static ProviderIdentity slack(
      Map<String, Object> tokenResponse, Map<String, Object> identityBody) {
    String teamId = string(identityBody, "team_id");
    if (!Boolean.TRUE.equals(identityBody.get("ok")) || teamId.isEmpty()) {
      throw new IllegalStateException("slack identity missing team");
    }
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("team_url", string(identityBody, "url"));
    return new ProviderIdentity(teamId, string(identityBody, "team"), metadata);
  }

  static ProviderIdentity notion(
      Map<String, Object> tokenResponse, Map<String, Object> identityBody) {
    String workspaceId = string(tokenResponse, "workspace_id");
    if (workspaceId.isEmpty()) {
      throw new IllegalStateException("notion identity missing workspace_id");
    }
    String workspaceName = string(tokenResponse, "workspace_name");
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("workspace_icon", string(tokenResponse, "workspace_icon"));
    return new ProviderIdentity(
        workspaceId, workspaceName.isEmpty() ? workspaceId : workspaceName, metadata);
  }

  static ProviderIdentity figma(
      Map<String, Object> tokenResponse, Map<String, Object> identityBody) {
    String userId = string(tokenResponse, "user_id_string");
    if (userId.isEmpty()) {
      throw new IllegalStateException("figma identity missing user_id");
    }
    return new ProviderIdentity(userId, "Figma user " + userId, Map.of());
  }

  private static String string(Map<String, Object> body, String key) {
    Object value = body == null ? null : body.get(key);
    return value == null ? "" : String.valueOf(value);
  }
}
