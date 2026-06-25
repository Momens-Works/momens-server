package works.momens.server.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard 모드 에러 응답 본문.
 *
 * <p>{@code { "error": { "code": ..., "message": ..., "details"?: ... } }} 형태입니다(규격:
 * docs/spec/api-response-error-codes.md). 전역 핸들러({@code app})와 SecurityFilterChain의 인증/인가 거부
 * 핸들러({@code auth})가 같은 shape를 내보내야 하므로 공유 에러 계약과 함께 {@code common}에 둡니다.
 *
 * <p>{@code details}가 없으면 {@link JsonInclude}로 필드 자체를 생략합니다.
 */
public record ErrorResponse(Body error) {

  public static ErrorResponse of(String code, String message, Object details) {
    return new ErrorResponse(new Body(code, message, details));
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Body(String code, String message, Object details) {}
}
