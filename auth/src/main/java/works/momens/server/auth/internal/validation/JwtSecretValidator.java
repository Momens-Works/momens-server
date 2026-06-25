package works.momens.server.auth.internal.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

class JwtSecretValidator implements ConstraintValidator<ValidJwtSecret, String> {

  private static final int MIN_SECRET_BYTES = 32;

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || value.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES;
  }
}
