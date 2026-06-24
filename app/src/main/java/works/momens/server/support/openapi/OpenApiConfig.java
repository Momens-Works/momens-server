package works.momens.server.support.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
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
}
