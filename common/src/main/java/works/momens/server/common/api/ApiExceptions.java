package works.momens.server.common.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** OpenAPI 실패 응답 예시 생성을 위해 controller method/docs interface method에 연결하는 annotation. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiExceptions {

  /** 문서화할 {@link ErrorCode} enum class 목록. */
  Class<? extends ErrorCode>[] value();
}
