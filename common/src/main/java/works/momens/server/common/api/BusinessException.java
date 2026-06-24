package works.momens.server.common.api;

import java.util.Map;
import lombok.Getter;

/**
 * 도메인·애플리케이션 규칙 위반을 나타내는 예외.
 *
 * <p>{@link ErrorCode}를 실어 던지면 {@code app}의 전역 예외 핸들러가 status·code·message로 렌더링합니다. 도메인 모듈은 자기
 * {@link ErrorCode} enum과 함께 이 예외를 던집니다.
 *
 * <p>{@code message}는 기본적으로 {@link ErrorCode#defaultMessage()}를 쓰고, 맥락이 필요하면 override 생성자로 덮습니다.
 * {@code details}는 필드 오류·리소스 식별자 등 부가 정보이며 민감 정보를 담지 않습니다.
 */
@Getter
public class BusinessException extends RuntimeException {

  private final transient ErrorCode errorCode;
  private final transient Map<String, Object> details;

  public BusinessException(ErrorCode errorCode) {
    this(errorCode, errorCode.defaultMessage(), null);
  }

  public BusinessException(ErrorCode errorCode, Map<String, Object> details) {
    this(errorCode, errorCode.defaultMessage(), details);
  }

  public BusinessException(ErrorCode errorCode, String message) {
    this(errorCode, message, null);
  }

  public BusinessException(ErrorCode errorCode, String message, Map<String, Object> details) {
    super(message);
    this.errorCode = errorCode;
    this.details = details;
  }
}
