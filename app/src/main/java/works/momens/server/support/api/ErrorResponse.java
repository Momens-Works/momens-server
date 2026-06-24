package works.momens.server.support.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard 모드 에러 응답 본문.
 *
 * <p>{@code { "error": { "code": ..., "message": ..., "details"?: ... } }} 형태입니다(규격:
 * docs/spec/api-response-error-codes.md). 이 shape는 전역 핸들러만 생성하므로 공유 모듈이 아닌 {@code app}에 둡니다.
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
