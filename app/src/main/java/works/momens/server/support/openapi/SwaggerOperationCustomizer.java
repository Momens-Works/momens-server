package works.momens.server.support.openapi;

import io.swagger.v3.oas.models.Operation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;
import works.momens.server.common.api.ApiException;

/** 컨트롤러 메서드 또는 docs interface에 선언된 {@link ApiException}을 읽어 OpenAPI 실패 응답 예시를 추가합니다. */
@RequiredArgsConstructor
public class SwaggerOperationCustomizer implements OperationCustomizer {

  private final ApiExceptionResolver apiExceptionResolver;

  private final SwaggerErrorExampleGenerator swaggerErrorExampleGenerator;

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    ApiException[] apiExceptions = findApiExceptions(handlerMethod);
    if (apiExceptions.length > 0) {
      swaggerErrorExampleGenerator.addErrorResponses(
          operation, apiExceptionResolver.resolve(apiExceptions));
    }
    return operation;
  }

  private ApiException[] findApiExceptions(HandlerMethod handlerMethod) {
    Method method = handlerMethod.getMethod();
    ApiException[] apiExceptions = method.getAnnotationsByType(ApiException.class);
    if (apiExceptions.length > 0) {
      return apiExceptions;
    }

    Class<?> beanType = handlerMethod.getBeanType();
    return interfaceTypes(beanType).stream()
        .map(interfaceType -> findInterfaceMethod(interfaceType, method))
        .filter(interfaceMethod -> interfaceMethod != null)
        .map(interfaceMethod -> interfaceMethod.getAnnotationsByType(ApiException.class))
        .filter(annotations -> annotations.length > 0)
        .findFirst()
        .orElse(new ApiException[0]);
  }

  private Set<Class<?>> interfaceTypes(Class<?> beanType) {
    Set<Class<?>> interfaceTypes = new LinkedHashSet<>();
    Class<?> currentType = beanType;
    while (currentType != null && currentType != Object.class) {
      collectInterfaces(currentType, interfaceTypes);
      currentType = currentType.getSuperclass();
    }
    return interfaceTypes;
  }

  private void collectInterfaces(Class<?> type, Set<Class<?>> interfaceTypes) {
    for (Class<?> interfaceType : type.getInterfaces()) {
      if (interfaceTypes.add(interfaceType)) {
        collectInterfaces(interfaceType, interfaceTypes);
      }
    }
  }

  private Method findInterfaceMethod(Class<?> interfaceType, Method targetMethod) {
    return Arrays.stream(interfaceType.getMethods())
        .filter(interfaceMethod -> hasSameSignature(interfaceMethod, targetMethod))
        .findFirst()
        .orElse(null);
  }

  private boolean hasSameSignature(Method interfaceMethod, Method targetMethod) {
    return interfaceMethod.getName().equals(targetMethod.getName())
        && Arrays.equals(interfaceMethod.getParameterTypes(), targetMethod.getParameterTypes());
  }
}
