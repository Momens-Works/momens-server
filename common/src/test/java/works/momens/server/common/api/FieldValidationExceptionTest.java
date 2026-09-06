package works.momens.server.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FieldValidationExceptionTest {

  @Test
  void createsStandardFieldValidationDetails() {
    BusinessException exception =
        FieldValidationException.forField("owner_user_ids", "must contain only workspace members");

    assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED);
    assertThat(exception.getMessage()).isEqualTo("요청 값이 유효하지 않습니다.");
    assertThat(exception.getDetails())
        .isEqualTo(
            new FieldValidationDetails(
                List.of(
                    new FieldValidationDetails.FieldViolation(
                        "owner_user_ids", "must contain only workspace members"))));
  }
}
