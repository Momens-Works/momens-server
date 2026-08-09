package works.momens.server.observability;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

/**
 * 지표 엔드포인트의 노출과 접근 제어(MOM-0833).
 *
 * <p>실제 노출은 local 프로필에만 있고(`application-local.yml`) test 프로필은 공통 기본값(`health`)을 따르므로, 여기서 같은 값을
 * 프로퍼티로 주입해 <b>노출됐을 때의 동작</b>을 고정한다.
 *
 * <p>확인하는 것은 둘이다. 엔드포인트를 열어도 {@code SecurityConfig}의 {@code PUBLIC_PATHS}에는 {@code
 * /actuator/health/**}만 있으므로 지표는 <b>보호 체인에 걸려 인증을 요구한다.</b> 그리고 인증하면 실제로 지표를 돌려준다. 앞의 것이 깨지면 지표가
 * 공개되고, 뒤의 것이 깨지면 열어 둔 의미가 없다.
 */
@SpringBootTest(properties = "management.endpoints.web.exposure.include=health,metrics")
@AutoConfigureMockMvc
class MetricsEndpointIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final String SECRET_HEADER = "X-Dev-Token-Secret";
  private static final String SECRET = "test-only-momens-auth-dev-token-secret";
  private static final String API_VERSION_HEADER = "API-Version";
  private static final String API_VERSION = "1";

  @Autowired private MockMvc mockMvc;

  @Test
  void requiresAuthenticationEvenWhenExposed() throws Exception {
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
  }

  @Test
  void servesMetricsToAuthenticatedCaller() throws Exception {
    mockMvc
        .perform(get("/actuator/metrics").header("Authorization", "Bearer " + devToken()))
        .andExpect(status().isOk())
        // 계측이 늘고 줄어도 흔들리지 않도록, 특정 지표가 아니라 JVM 기본 지표로 응답을 확인한다.
        .andExpect(jsonPath("$.names").value(hasItem("jvm.memory.used")));
  }

  @Test
  void keepsHealthPublic() throws Exception {
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  private String devToken() throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/auth/dev/token")
                    .header(SECRET_HEADER, SECRET)
                    .header(API_VERSION_HEADER, API_VERSION))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.access_token");
  }
}
