package works.momens.server.support.api;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.common.api.ErrorCode;

/**
 * Standard 모드 에러 응답을 한곳에서 렌더링하는 전역 예외 핸들러.
 *
 * <p>도메인·애플리케이션 예외({@link BusinessException})와 프레임워크의 요청 검증/파싱 예외를
 * docs/spec/api-response-error-codes.md의 공통 에러 코드로 매핑합니다. 내부 예외 메시지·stack trace·SQL·secret은 응답에
 * 노출하지 않습니다.
 *
 * <p>인증/인가 거부(401/403) 본문은 필터 단계에서 발생해 여기서 잡히지 않습니다. SecurityFilterChain의 entry point/handler 배선은
 * 인증/인가 구현 시점(MOM-8)에 추가합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    log.warn("business error code={} status={}", errorCode.code(), errorCode.status());
    return render(errorCode, e.getMessage(), e.getDetails());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    List<FieldErrorDetail> fields =
        e.getBindingResult().getFieldErrors().stream()
            .map(GlobalExceptionHandler::toFieldErrorDetail)
            .toList();
    log.debug("validation failed fields={}", fields.size());
    return render(
        CommonErrorCode.COMMON_VALIDATION_FAILED,
        CommonErrorCode.COMMON_VALIDATION_FAILED.defaultMessage(),
        Map.of("fields", fields));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class
  })
  public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
    log.debug("bad request type={}", e.getClass().getSimpleName());
    return render(
        CommonErrorCode.COMMON_BAD_REQUEST,
        CommonErrorCode.COMMON_BAD_REQUEST.defaultMessage(),
        null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    log.error("unexpected error", e);
    return render(
        CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR,
        CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR.defaultMessage(),
        null);
  }

  private ResponseEntity<ErrorResponse> render(
      ErrorCode errorCode, String message, Object details) {
    return ResponseEntity.status(errorCode.status())
        .body(ErrorResponse.of(errorCode.code(), message, details));
  }

  private static FieldErrorDetail toFieldErrorDetail(FieldError fieldError) {
    return new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage());
  }

  /** {@code details.fields} 원소: 검증 실패한 필드와 사유. */
  private record FieldErrorDetail(String field, String reason) {}
}
