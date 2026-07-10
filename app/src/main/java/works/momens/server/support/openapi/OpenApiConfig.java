package works.momens.server.support.openapi;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiConfig {

  @Bean
  public OpenAPI openAPI(OpenApiProperties properties) {
    return new OpenAPI()
        .info(
            new Info()
                .title("Momens Server API")
                .version("v1")
                .description("Momens Java Spring 제품 API 서버 문서"))
        .servers(
            List.of(new Server().url(properties.serverUrl()).description("Configured Server")));
  }

  @Bean
  public OperationCustomizer swaggerOperationCustomizer() {
    return new SwaggerOperationCustomizer(new SwaggerErrorExampleGenerator());
  }

  /**
   * DTO는 런타임 직렬화에 Jackson 3 {@code @JsonNaming(SnakeCaseStrategy)}를 쓰지만, 스키마를 만드는 swagger-core는
   * Jackson 2라 이 애노테이션을 읽지 못해(swagger-core#4991) 스키마 property가 wire format(snake_case)이 아니라 Java
   * 필드명(camelCase)으로 나온다. swagger-core가 introspection에 쓰는 Jackson 2 ObjectMapper에 직접 snake_case 전략을
   * 걸어, 런타임 규칙과 동일하게 스키마 property를 전역 snake_case로 생성한다. 전 API가 균일한 snake_case라 전역 전략으로 충분하며, 특정 필드만
   * 다르게 두려면 Jackson 2 {@code @JsonProperty}로 개별 override할 수 있다.
   */
  @Bean
  public ModelResolver modelResolver(ObjectMapperProvider objectMapperProvider) {
    return new ModelResolver(
        objectMapperProvider
            .jsonMapper()
            .copy()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE));
  }
}
