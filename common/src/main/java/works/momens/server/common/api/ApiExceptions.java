package works.momens.server.common.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 같은 메서드에 {@link ApiException}을 여러 번 선언하면 이를 담는 컨테이너 애너테이션입니다. 에러 코드는 {@link ApiException}으로 선언하며,
 * 이 애너테이션은 직접 사용하지 않습니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiExceptions {

  ApiException[] value();
}
