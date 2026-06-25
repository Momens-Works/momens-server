package works.momens.server.support.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import works.momens.server.auth.internal.JwtTokenService;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/**
 * SecurityFilterChain(MOM-8) 실배선 통합테스트.
 *
 * <p>auth가 발급한 HS256 access로 보호 엔드포인트(`/api/me`)가 실제로 인증되고 `Principal.name=userId` seam이 동작하는지,
 * 미인증/위조 요청이 Standard 에러 shape의 401을 내는지 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenService jwtTokenService;
  @Autowired private UserService userService;

  @Test
  void returnsProfileWhenAccessTokenValid() throws Exception {
    UserProfile user = userService.findOrCreate("auth-it@momens.works", "규일", null);
    String token = jwtTokenService.issueAccessToken(user.id());

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(user.id().toString()));
  }

  @Test
  void returnsStandardUnauthorizedWhenNoToken() throws Exception {
    mockMvc
        .perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
  }

  @Test
  void returnsStandardUnauthorizedWhenTokenMalformed() throws Exception {
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
  }
}
