package works.momens.server.support.openapi;

import io.swagger.v3.oas.models.Operation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import works.momens.server.common.api.ApiExceptions;

/** controller method 또는 docs interface의 {@link ApiExceptions}를 읽어 실패 응답 예시를 보강합니다. */
@RequiredArgsConstructor
public class SwaggerOperationCustomizer implements OperationCustomizer {

  private final SwaggerErrorExampleGenerator swaggerErrorExampleGenerator;

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    ApiExceptions apiExceptions = findApiExceptions(handlerMethod);
    if (apiExceptions != null) {
      swaggerErrorExampleGenerator.addErrorResponses(operation, apiExceptions.value());
    }
    return operation;
  }

  private ApiExceptions findApiExceptions(HandlerMethod handlerMethod) {
    ApiExceptions apiExceptions = handlerMethod.getMethodAnnotation(ApiExceptions.class);
    if (apiExceptions != null) {
      return apiExceptions;
    }

    Method method = handlerMethod.getMethod();
    Class<?> beanType = handlerMethod.getBeanType();
    return interfaceTypes(beanType).stream()
        .map(interfaceType -> findInterfaceMethod(interfaceType, method))
        .filter(interfaceMethod -> interfaceMethod != null)
        .map(
            interfaceMethod ->
                AnnotatedElementUtils.findMergedAnnotation(interfaceMethod, ApiExceptions.class))
        .filter(annotation -> annotation != null)
        .findFirst()
        .orElse(null);
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
