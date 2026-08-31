package works.momens.server.common.api;

import java.util.List;

/** 필드 검증 실패의 Standard {@code error.details} 계약. */
public record FieldValidationDetails(List<FieldViolation> fields) {

  public FieldValidationDetails {
    fields = List.copyOf(fields);
  }

  /** 검증에 실패한 JSON 필드와 사유. */
  public record FieldViolation(String field, String reason) {}
}
