package works.momens.server.auth.internal.config.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** HS256 secret의 UTF-8 바이트 길이 하한을 검증합니다. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JwtSecretValidator.class)
public @interface ValidJwtSecret {

  String message() default "must be at least 32 bytes for HS256";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
