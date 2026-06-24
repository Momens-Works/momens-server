package works.momens.server.support.api;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.common.api.ErrorCode;

/**
 * Standard 모드 에러 응답을 한곳에서 렌더링하는 전역 예외 핸들러.
 *
 * <p>{@link ResponseEntityExceptionHandler}를 상속해 Spring MVC 표준 예외(405/415/400 등)는 프레임워크가 정한 HTTP
 * status를 보존하고, body만 docs/spec/api-response-error-codes.md의 Standard 에러 shape로 통일합니다. 도메인·애플리케이션
 * 예외({@link BusinessException})와 예상 밖 예외(500)는 직접 처리합니다. 내부 예외 메시지·stack trace·SQL·secret은 응답에 노출하지
 * 않습니다.
 *
 * <p>인증/인가 거부(401/403) 본문은 필터 단계에서 발생해 여기서 잡히지 않습니다. SecurityFilterChain의 entry point/handler 배선은
 * 인증/인가 구현 시점(MOM-8)에 추가합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    log.warn("business error code={} status={}", errorCode.code(), errorCode.status());
    return ResponseEntity.status(errorCode.status())
        .body(ErrorResponse.of(errorCode.code(), e.getMessage(), e.getDetails()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    ErrorCode errorCode = CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR;
    log.error("unexpected error", e);
    return ResponseEntity.status(errorCode.status())
        .body(ErrorResponse.of(errorCode.code(), errorCode.defaultMessage(), null));
  }

  /** Bean Validation 실패는 {@code details.fields}로 풍부하게 매핑합니다. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldErrorDetail> fields =
        ex.getBindingResult().getFieldErrors().stream()
            .map(GlobalExceptionHandler::toFieldErrorDetail)
            .toList();
    log.debug("validation failed fields={}", fields.size());
    ErrorCode errorCode = CommonErrorCode.COMMON_VALIDATION_FAILED;
    Object body =
        ErrorResponse.of(errorCode.code(), errorCode.defaultMessage(), Map.of("fields", fields));
    return ResponseEntity.status(status).headers(headers).body(body);
  }

  /**
   * 그 외 Spring MVC 표준 예외 공통 처리.
   *
   * <p>프레임워크가 정한 status와 헤더(405의 {@code Allow}, 415의 {@code Accept} 등)는 보존하고 body만 Standard shape로
   * 교체합니다. 매핑되지 않은 드문 status는 status 계열에 맞춰 4xx는 {@code COMMON_BAD_REQUEST}, 5xx는 {@code
   * COMMON_INTERNAL_SERVER_ERROR}로 떨어집니다(해당 예외를 던지는 엔드포인트가 생기면 전용 코드를 추가).
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    if (statusCode.is5xxServerError()) {
      log.error(
          "server error status={} type={}", statusCode.value(), ex.getClass().getSimpleName(), ex);
    } else {
      log.debug(
          "request error status={} type={}", statusCode.value(), ex.getClass().getSimpleName());
    }
    ErrorCode errorCode = toErrorCode(statusCode);
    Object responseBody = ErrorResponse.of(errorCode.code(), errorCode.defaultMessage(), null);
    return ResponseEntity.status(statusCode).headers(headers).body(responseBody);
  }

  private static ErrorCode toErrorCode(HttpStatusCode statusCode) {
    return switch (statusCode.value()) {
      case 400 -> CommonErrorCode.COMMON_BAD_REQUEST;
      case 404 -> CommonErrorCode.COMMON_NOT_FOUND;
      case 405 -> CommonErrorCode.COMMON_METHOD_NOT_ALLOWED;
      case 415 -> CommonErrorCode.COMMON_UNSUPPORTED_MEDIA_TYPE;
      // 미매핑 status는 status 계열에 맞춰 폴백(4xx에 서버 오류 코드가 붙는 모순 방지).
      // 해당 예외를 던지는 엔드포인트가 실제로 생기면 전용 코드를 추가한다.
      default ->
          statusCode.is5xxServerError()
              ? CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR
              : CommonErrorCode.COMMON_BAD_REQUEST;
    };
  }

  private static FieldErrorDetail toFieldErrorDetail(FieldError fieldError) {
    return new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage());
  }

  /** {@code details.fields} 원소: 검증 실패한 필드와 사유. */
  private record FieldErrorDetail(String field, String reason) {}
}
