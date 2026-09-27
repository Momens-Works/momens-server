package works.momens.server.support.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import works.momens.server.auth.AccessTokenTestFactory;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

@SpringBootTest(properties = "momens.mcp.oauth.consent-uri=https://app.example.com/oauth/authorize")
@AutoConfigureMockMvc
class McpOAuthSecurityIntegrationTest extends AbstractPostgresIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired AccessTokenTestFactory accessTokens;

  @Test
  void consentUsesTheExistingUserCookieAndMcpTokensCannotAuthenticateUserApis() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, email, name) VALUES (?, ?, 'OAuth user')",
        userId,
        userId + "@example.com");
    jdbc.update(
        "INSERT INTO workspaces (id, name, slug) VALUES (?, 'OAuth workspace', ?)",
        workspaceId,
        workspaceId.toString());
    jdbc.update(
        "INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, 'member')",
        workspaceId,
        userId);
    String registered =
        mvc.perform(
                post("/api/oauth2/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        mapper.writeValueAsString(
                            Map.of(
                                "client_name",
                                "security test",
                                "redirect_uris",
                                List.of("http://localhost:3000/callback")))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String clientId = mapper.readTree(registered).get("client_id").stringValue();
    String consent =
        mvc.perform(
                get("/api/oauth2/authorize")
                    .queryParam("response_type", "code")
                    .queryParam("client_id", clientId)
                    .queryParam("redirect_uri", "http://localhost:3000/callback")
                    .queryParam("code_challenge_method", "S256")
                    .queryParam("code_challenge", "ZtNPunH49FD35FWYhT5Tv8I7vRKQJ8uxMaL0_9eHjNA")
                    .queryParam("resource", "https://api.momens.works/api/mcp"))
            .andExpect(status().isFound())
            .andReturn()
            .getResponse()
            .getRedirectedUrl();
    String interaction =
        UriComponentsBuilder.fromUriString(consent)
            .build()
            .getQueryParams()
            .getFirst("interaction");
    String path = "/api/oauth/interactions/" + interaction;
    mvc.perform(get(path))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    Cookie cookie = new Cookie("access_token", accessTokens.issueAccessToken(userId));
    mvc.perform(get(path).cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interaction.workspaces[0].id").value(workspaceId.toString()));
    // Production uses the existing SameSite-cookie policy and CORS filter, without a CSRF token.
    for (String origin : List.of("https://evil.example", "https://untrusted.momens.works")) {
      mvc.perform(
              post(path + "/approve")
                  .cookie(cookie)
                  .header("Origin", origin)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(Map.of("workspace_id", workspaceId))))
          .andExpect(status().isForbidden());
      mvc.perform(post(path + "/deny").cookie(cookie).header("Origin", origin))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get(path).cookie(cookie)).andExpect(status().isOk());
    String approval =
        mvc.perform(
                post(path + "/approve")
                    .header("Origin", "http://localhost:3000")
                    .cookie(cookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("workspace_id", workspaceId))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String code =
        UriComponentsBuilder.fromUriString(
                mapper.readTree(approval).get("redirect_to").stringValue())
            .build()
            .getQueryParams()
            .getFirst("code");
    String pair =
        mvc.perform(
                post("/api/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "authorization_code")
                    .param("client_id", clientId)
                    .param("code", code)
                    .param("code_verifier", "a".repeat(43))
                    .param("redirect_uri", "http://localhost:3000/callback")
                    .param("resource", "https://api.momens.works/api/mcp"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = mapper.readTree(pair).get("access_token").stringValue();
    assertThat(mapper.readTree(pair).get("refresh_token").stringValue()).isNotBlank();
    UUID projectId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO projects (id, workspace_id, name, owner_id, label) VALUES (?, ?, 'MCP project', ?, 'PRJ-0991')",
        projectId,
        workspaceId,
        userId);
    jdbc.update(
        "INSERT INTO tasks (id, workspace_id, project_id, title, status, priority, label) VALUES (?, ?, ?, 'Read tools', 'todo', 'medium', 'MOM-0991')",
        taskId,
        workspaceId,
        projectId);
    mvc.perform(mcpRequest(token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.tools.length()").value(5));
    mvc.perform(toolRequest(token, "list_projects", Map.of()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.result.content[0].text")
                .value("1 project(s):\n- PRJ-0991 MCP project [active]"));
    mvc.perform(toolRequest(token, "list_members", Map.of()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.content[0].text").value(containsString("OAuth user")));
    mvc.perform(toolRequest(token, "list_milestones", Map.of()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.content[0].text").value("No milestones match."));
    mvc.perform(toolRequest(token, "list_tasks", Map.of("project", "PRJ-0991")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.result.content[0].text")
                .value("1 task(s):\n- MOM-0991 · Read tools [todo] — PRJ-0991 MCP project"));
    mvc.perform(toolRequest(token, "get_task", Map.of("task", "mom-0991")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.result.content[0].text")
                .value(
                    "MOM-0991 · Read tools\nstatus: todo · priority: medium · project: PRJ-0991 MCP project"));
    mvc.perform(toolRequest(token, "get_task", Map.of("task", taskId.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.isError").doesNotExist());

    jdbc.update("UPDATE projects SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", projectId);
    for (String reference : List.of(taskId.toString(), "mom-0991")) {
      mvc.perform(toolRequest(token, "get_task", Map.of("task", reference)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.isError").value(true))
          .andExpect(
              jsonPath("$.result.content[0].text")
                  .value("No task in this workspace: " + reference));
    }

    mvc.perform(mcpRequest(cookie.getValue())).andExpect(status().isUnauthorized());
    mvc.perform(mcpRequest(null).cookie(cookie)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    mvc.perform(
            post("/api/oauth2/revoke")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("client_id", clientId)
                .param("token", token))
        .andExpect(status().isOk());
    mvc.perform(mcpRequest(token)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/me").cookie(cookie)).andExpect(status().isOk());
  }

  private MockHttpServletRequestBuilder toolRequest(
      String token, String name, Map<String, String> arguments) {
    return post("/api/mcp")
        .header("Authorization", "Bearer " + token)
        .header("MCP-Protocol-Version", "2026-07-28")
        .header("Mcp-Method", "tools/call")
        .header("Mcp-Name", name)
        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            mapper.writeValueAsString(
                Map.of(
                    "jsonrpc",
                    "2.0",
                    "id",
                    2,
                    "method",
                    "tools/call",
                    "params",
                    Map.of(
                        "name",
                        name,
                        "arguments",
                        arguments,
                        "_meta",
                        Map.of(
                            "io.modelcontextprotocol/protocolVersion",
                            "2026-07-28",
                            "io.modelcontextprotocol/clientCapabilities",
                            Map.of())))));
  }

  private MockHttpServletRequestBuilder mcpRequest(String token) {
    MockHttpServletRequestBuilder request =
        post("/api/mcp")
            .header("MCP-Protocol-Version", "2026-07-28")
            .header("Mcp-Method", "tools/list")
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
            {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{
              "io.modelcontextprotocol/protocolVersion":"2026-07-28",
              "io.modelcontextprotocol/clientCapabilities":{}}}}
            """);
    return token == null ? request : request.header("Authorization", "Bearer " + token);
  }
}
