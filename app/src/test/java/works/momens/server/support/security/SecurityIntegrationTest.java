package works.momens.server.support.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import works.momens.server.auth.AccessTokenTestFactory;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/**
 * SecurityFilterChain(MOM-8) 실배선 통합테스트.
 *
 * <p>auth가 발급한 HS256 access로 보호 엔드포인트(`/api/me`)가 실제로 인증되고 `Principal.name=userId` seam이 동작하는지,
 * 미인증/위조/만료 요청이 규격대로 401(인증정보 없음=AUTH_UNAUTHORIZED, 토큰 검증 실패=AUTH_INVALID_TOKEN)을 내는지 확인합니다.
 *
 * <p>토큰은 auth의 public testFixtures({@link AccessTokenTestFactory})로 만들어 모듈 경계를 지킵니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenTestFactory accessTokens;
  @Autowired private UserService userService;

  @Test
  void returnsProfileWhenAccessTokenValid() throws Exception {
    UserProfile user = userService.findOrCreate("auth-it@momens.works", "규일", null);
    String token = accessTokens.issueAccessToken(user.id());

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
  void returnsInvalidTokenWhenMalformed() throws Exception {
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
  }

  @Test
  void returnsInvalidTokenWhenSignatureForged() throws Exception {
    String forged = tamperSignature(accessTokens.issueAccessToken(UUID.randomUUID()));

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + forged))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
  }

  @Test
  void returnsInvalidTokenWhenExpired() throws Exception {
    String expired = accessTokens.issueExpiredAccessToken(UUID.randomUUID());

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
  }

  /** 서명 세그먼트의 마지막 문자를 바꿔 구조는 유효하나 서명이 어긋난 토큰을 만듭니다. */
  private static String tamperSignature(String jwt) {
    int lastDot = jwt.lastIndexOf('.');
    String signature = jwt.substring(lastDot + 1);
    char last = signature.charAt(signature.length() - 1);
    char replacement = (last == 'A') ? 'B' : 'A';
    return jwt.substring(0, lastDot + 1)
        + signature.substring(0, signature.length() - 1)
        + replacement;
  }
}
