package works.momens.server.support.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

/**
 * springdoc이 의존하는 swagger-core는 아직 Jackson 3({@code tools.jackson.*})를 지원하지 않아, DTO에 붙은
 * {@code @JsonNaming(SnakeCaseStrategy)}를 인식하지 못한다(swagger-core#4991, springdoc-openapi#3157). 그 결과
 * {@code /v3/api-docs} 스키마의 property 키가 실제 wire format(snake_case, 예: {@code id_token})이 아니라 원본
 * Java 필드명 (camelCase, 예: {@code idToken})으로 나가 Swagger UI의 request body 예시가 실제 API와 다른 필드명으로
 * 렌더링된다. 이 테스트는 {@code GoogleTokenRequest} 스키마의 property 키가 snake_case이고 필드별 {@code @Schema(example
 * = ...)} 값이 그 키 아래에 노출되는지로 회귀를 재현/검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsExampleTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void googleTokenRequestSchemaUsesSnakeCasePropertyKeysWithExamples() throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode root = new ObjectMapper().readTree(body);
    JsonNode schema = root.path("components").path("schemas").path("GoogleTokenRequest");

    assertThat(schema.isMissingNode())
        .as("GoogleTokenRequest schema should be present in /v3/api-docs")
        .isFalse();

    JsonNode properties = schema.path("properties");
    assertThat(properties.has("idToken"))
        .as(
            "schema property keys should be snake_case (id_token), not the camelCase Java field"
                + " name (idToken): %s",
            properties)
        .isFalse();

    JsonNode idTokenExample = properties.path("id_token").path("example");
    JsonNode deviceExample = properties.path("device").path("example");

    assertThat(idTokenExample.isMissingNode() || idTokenExample.isNull())
        .as("id_token example should be populated, but was: %s", idTokenExample)
        .isFalse();
    assertThat(deviceExample.isMissingNode() || deviceExample.isNull())
        .as("device example should be populated, but was: %s", deviceExample)
        .isFalse();

    JsonNode tokenResponseProperties =
        root.path("components").path("schemas").path("TokenResponse").path("properties");
    assertThat(tokenResponseProperties.has("accessToken"))
        .as(
            "TokenResponse schema property keys should be snake_case (access_token), not"
                + " camelCase (accessToken): %s",
            tokenResponseProperties)
        .isFalse();
    assertThat(tokenResponseProperties.has("access_token"))
        .as("TokenResponse schema should expose access_token: %s", tokenResponseProperties)
        .isTrue();
  }

  /**
   * 새 DTO나 새 필드에서 {@code @JsonProperty("snake_case")}를 빠뜨리면 swagger-core가 Java 필드명(camelCase)을 그대로
   * property 키로 노출하므로, 전체 스키마를 스캔해 누락을 구조적으로 잡는다.
   */
  @Test
  void allSchemaPropertyKeysAreSnakeCase() throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode schemas = new ObjectMapper().readTree(body).path("components").path("schemas");
    List<String> offenders = new ArrayList<>();
    for (Map.Entry<String, JsonNode> schema : schemas.properties()) {
      collectCamelCaseKeys(schema.getKey(), schema.getValue(), offenders);
    }

    assertThat(offenders)
        .as("schema property keys must be snake_case; add @JsonProperty to the DTO field")
        .isEmpty();
  }

  private void collectCamelCaseKeys(String path, JsonNode schema, List<String> offenders) {
    for (Map.Entry<String, JsonNode> property : schema.path("properties").properties()) {
      if (property.getKey().chars().anyMatch(Character::isUpperCase)) {
        offenders.add(path + "." + property.getKey());
      }
      collectCamelCaseKeys(path + "." + property.getKey(), property.getValue(), offenders);
    }
    if (schema.has("items")) {
      collectCamelCaseKeys(path + "[]", schema.get("items"), offenders);
    }
  }
}
