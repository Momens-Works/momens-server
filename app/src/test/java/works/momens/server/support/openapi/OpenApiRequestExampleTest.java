package works.momens.server.support.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiRequestExampleTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;

  /**
   * swagger-annotations와 swagger-core의 버전이 다르면 {@code @Schema}의 기본값 sentinel을 잘못 해석해 request
   * schema에 {@code "default": null}을 만든다. Swagger UI는 이를 우선해 request body 예시 전체를 {@code null}로
   * 그리므로, 대표 request schema로 의존성 버전 정렬을 검증한다.
   */
  @Test
  void requestSchemaDoesNotCarrySpuriousNullDefault() throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode root = new ObjectMapper().readTree(body);
    assertThat(root.path("openapi").asString()).startsWith("3.1.");

    JsonNode schema = root.path("components").path("schemas").path("GoogleTokenRequest");
    assertThat(schema.isMissingNode()).isFalse();
    assertThat(schema.has("default"))
        .as(
            "default must be absent; default: null makes Swagger UI render the request example as null")
        .isFalse();
  }
}
