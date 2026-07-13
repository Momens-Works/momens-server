package works.momens.server.auth.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.user.UserService;

/**
 * dev 전용 토큰 발급 엔드포인트(MOM-90) 통합테스트. test 프로필은 {@link
 * works.momens.server.auth.internal.config.DevOnly} 대상이라 엔드포인트가 등록됩니다.
 *
 * <p>공유 시크릿과 allowlist는 {@code application-test.yml}의 {@code momens.auth.dev-token}에서 옵니다. 발급된 토큰이
 * 운영과 같은 HS256이라 보호 API(`/api/me`)에서 실제로 인증되는지까지 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DevTokenIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final String SECRET_HEADER = "X-Dev-Token-Secret";
  private static final String SECRET = "test-only-momens-auth-dev-token-secret";
  private static final String API_VERSION_HEADER = "API-Version";
  private static final String API_VERSION = "1";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserService userService;

  @Test
  void issuesTokenForDefaultAllowlistUserThatAuthenticatesProtectedApi() throws Exception {
    UUID firstAllowedId =
        userService.findOrCreate("jinsu@momens.works", "jinsu@momens.works", null).id();

    String response =
        mockMvc
            .perform(
                post("/api/auth/dev/token")
                    .header(SECRET_HEADER, SECRET)
                    .header(API_VERSION_HEADER, API_VERSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String token = JsonPath.read(response, "$.access_token");
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(firstAllowedId.toString()));
  }

  @Test
  void issuesTokenForRequestedAllowlistEmail() throws Exception {
    UUID gyuilId = userService.findOrCreate("gyuil@momens.works", "gyuil@momens.works", null).id();

    String response =
        mockMvc
            .perform(
                post("/api/auth/dev/token")
                    .header(SECRET_HEADER, SECRET)
                    .header(API_VERSION_HEADER, API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"gyuil@momens.works\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String token = JsonPath.read(response, "$.access_token");
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(gyuilId.toString()));
  }

  @Test
  void rejectsWrongSecret() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/dev/token")
                .header(SECRET_HEADER, "wrong-secret")
                .header(API_VERSION_HEADER, API_VERSION))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_DEV_TOKEN_SECRET_INVALID"));
  }

  @Test
  void rejectsMissingSecret() throws Exception {
    mockMvc
        .perform(post("/api/auth/dev/token").header(API_VERSION_HEADER, API_VERSION))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_DEV_TOKEN_SECRET_INVALID"));
  }

  @Test
  void rejectsEmailNotInAllowlist() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/dev/token")
                .header(SECRET_HEADER, SECRET)
                .header(API_VERSION_HEADER, API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"stranger@momens.works\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_DEV_TOKEN_EMAIL_NOT_ALLOWED"));
  }
}
