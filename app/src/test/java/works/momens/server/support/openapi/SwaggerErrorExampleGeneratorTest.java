package works.momens.server.support.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.MediaType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import works.momens.server.common.api.CommonErrorCode;

class SwaggerErrorExampleGeneratorTest {

  private final SwaggerErrorExampleGenerator generator = new SwaggerErrorExampleGenerator();

  @Test
  void addsStandardErrorExamplesByStatus() {
    Operation operation = new Operation();

    generator.addErrorResponses(operation, CommonErrorCode.class);

    MediaType badRequest = operation.getResponses().get("400").getContent().get("application/json");
    Example validationExample = badRequest.getExamples().get("COMMON_VALIDATION_FAILED");

    assertThat(operation.getResponses()).containsKeys("400", "401", "403", "404", "405", "409");
    assertThat(validationExample.getSummary()).isEqualTo("COMMON_VALIDATION_FAILED");
    assertThat(validationExample.getValue())
        .isEqualTo(
            Map.of(
                "error",
                Map.of("code", "COMMON_VALIDATION_FAILED", "message", "요청 값이 유효하지 않습니다.")));
  }
}
