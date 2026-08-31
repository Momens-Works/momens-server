package works.momens.server.common.api;

import java.util.List;

/** 필드 검증 실패를 Standard {@code details.fields[]} 계약으로 만드는 예외. */
public final class FieldValidationException extends BusinessException {

  public static final String DEFAULT_REASON = "invalid value";

  private FieldValidationException(FieldValidationDetails details) {
    super(CommonErrorCode.COMMON_VALIDATION_FAILED, details);
  }

  public static FieldValidationException forField(String field) {
    return forField(field, DEFAULT_REASON);
  }

  public static FieldValidationException forField(String field, String reason) {
    return new FieldValidationException(
        new FieldValidationDetails(
            List.of(new FieldValidationDetails.FieldViolation(field, reason))));
  }
}
