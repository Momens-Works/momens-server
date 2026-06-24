package works.momens.server.support.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Operation;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import works.momens.server.common.api.ApiExceptions;
import works.momens.server.common.api.CommonErrorCode;

class SwaggerOperationCustomizerTest {

  private final SwaggerOperationCustomizer customizer =
      new SwaggerOperationCustomizer(new SwaggerErrorExampleGenerator());

  @Test
  void readsApiExceptionsFromControllerDocsInterface() throws NoSuchMethodException {
    InterfaceAnnotatedController controller = new InterfaceAnnotatedController();
    Method method = InterfaceAnnotatedController.class.getMethod("get");
    HandlerMethod handlerMethod = new HandlerMethod(controller, method);

    Operation operation = customizer.customize(new Operation(), handlerMethod);

    assertThat(
            operation.getResponses().get("401").getContent().get("application/json").getExamples())
        .containsKey("AUTH_UNAUTHORIZED");
  }

  @Test
  void readsApiExceptionsFromControllerMethod() throws NoSuchMethodException {
    MethodAnnotatedController controller = new MethodAnnotatedController();
    Method method = MethodAnnotatedController.class.getMethod("get");
    HandlerMethod handlerMethod = new HandlerMethod(controller, method);

    Operation operation = customizer.customize(new Operation(), handlerMethod);

    assertThat(
            operation.getResponses().get("404").getContent().get("application/json").getExamples())
        .containsKey("COMMON_NOT_FOUND");
  }

  private interface InterfaceAnnotatedControllerDocs {

    @ApiExceptions({CommonErrorCode.class})
    void get();
  }

  private static class InterfaceAnnotatedController implements InterfaceAnnotatedControllerDocs {

    @Override
    public void get() {}
  }

  private static class MethodAnnotatedController {

    @ApiExceptions({CommonErrorCode.class})
    public void get() {}
  }
}
