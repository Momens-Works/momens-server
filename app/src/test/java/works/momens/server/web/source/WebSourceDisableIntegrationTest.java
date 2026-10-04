package works.momens.server.web.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import works.momens.server.auth.AccessTokenTestFactory;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.user.UserService;

@SpringBootTest
@AutoConfigureMockMvc
class WebSourceDisableIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private AccessTokenTestFactory accessTokens;
  @Autowired private UserService users;
  @Autowired private JdbcTemplate jdbc;

  @ParameterizedTest
  @ValueSource(strings = {"admin", "owner"})
  void authorizedDisablePreservesSuccessAndRepeatUsesStandardError(String role) throws Exception {
    Fixture fixture = fixture(role);
    mvc.perform(authorized(fixture.connectionId(), fixture.userId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("disabled"));
    Map<String, Object> disabled = row(fixture.connectionId());
    assertThat(disabled.get("status")).isEqualTo("DISABLED");
    assertThat(disabled.get("disabled_at")).isNotNull();
    mvc.perform(authorized(fixture.connectionId(), fixture.userId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("SOURCE_CONNECTION_ALREADY_DISABLED"));
    assertThat(row(fixture.connectionId())).isEqualTo(disabled);
  }

  @ParameterizedTest
  @ValueSource(strings = {"member", "outsider"})
  void insufficientRoleDoesNotChangeConnection(String role) throws Exception {
    Fixture fixture = fixture(role);
    Map<String, Object> before = row(fixture.connectionId());
    mvc.perform(authorized(fixture.connectionId(), fixture.userId()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"))
        .andExpect(jsonPath("$.error.details.required_role").value("admin"));
    assertThat(row(fixture.connectionId())).isEqualTo(before);
  }

  @Test
  void missingAuthenticationDoesNotChangeConnection() throws Exception {
    Fixture fixture = fixture("owner");
    Map<String, Object> before = row(fixture.connectionId());
    mvc.perform(post("/api/source-connections/{id}/disable", fixture.connectionId()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    assertThat(row(fixture.connectionId())).isEqualTo(before);
  }

  @Test
  void invalidAndMissingIdsUseStandardErrors() throws Exception {
    Fixture fixture = fixture("owner");
    mvc.perform(authorized("invalid", fixture.userId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_BAD_REQUEST"));
    mvc.perform(authorized(UUID.randomUUID(), fixture.userId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("SOURCE_CONNECTION_NOT_FOUND"));
  }

  @Test
  void figmaMissingCredentialsStillDisables() throws Exception {
    Fixture fixture = fixture("owner");
    jdbc.update(
        "UPDATE source_connections SET source_type = 'FIGMA',"
            + " metadata = '{\"webhook_id\":\"wh-no-token\"}'::jsonb WHERE id = ?",
        fixture.connectionId());
    mvc.perform(authorized(fixture.connectionId(), fixture.userId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("disabled"));
    assertThat(row(fixture.connectionId()).get("status")).isEqualTo("DISABLED");
  }

  private MockHttpServletRequestBuilder authorized(Object id, UUID userId) {
    return post("/api/source-connections/{id}/disable", id)
        .header("API-Version", "1")
        .header("Authorization", "Bearer " + accessTokens.issueAccessToken(userId));
  }

  private Fixture fixture(String role) {
    UUID userId =
        users.findOrCreate("disable-" + UUID.randomUUID() + "@example.com", "테스트", null).id();
    UUID workspaceId = UUID.randomUUID();
    UUID connectionId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO workspaces (id, name, slug) VALUES (?, 'disable', ?)",
        workspaceId,
        "disable-" + workspaceId);
    if (!role.equals("outsider")) {
      jdbc.update(
          "INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, ?)",
          workspaceId,
          userId,
          role);
    }
    jdbc.update(
        "INSERT INTO source_connections (id, workspace_id, source_type, status)"
            + " VALUES (?, ?, 'GITHUB', 'ACTIVE')",
        connectionId,
        workspaceId);
    return new Fixture(userId, connectionId);
  }

  private Map<String, Object> row(UUID id) {
    return jdbc.queryForMap("SELECT * FROM source_connections WHERE id = ?", id);
  }

  private record Fixture(UUID userId, UUID connectionId) {}
}
