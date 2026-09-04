package works.momens.server.common.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * OpenAPI 실패 응답 예시로 문서화할 에러 코드를 컨트롤러 메서드 또는 docs interface 메서드에 선언하는 애너테이션입니다. 같은 메서드에 여러 번 선언할 수
 * 있으며, 두 번 이상 선언하면 컴파일러가 {@link ApiExceptions} 컨테이너에 자동으로 담습니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiExceptions.class)
public @interface ApiException {

  /** 문서화할 {@link ErrorCode} enum 클래스입니다. */
  Class<? extends ErrorCode> value();

  /**
   * 문서화할 에러 코드는 {@link ErrorCode#code()} 값으로 지정합니다. 값을 비워 두면 해당 enum의 모든 에러 코드가 문서화됩니다. 원칙적으로
   * 엔드포인트에서 실제로 반환할 수 있는 코드만 지정하며, 공통 에러 코드처럼 엔드포인트별 범위가 아직 정해지지 않은 경우에만 값을 비워 둡니다.
   */
  String[] codes() default {};
}
