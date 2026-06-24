package works.momens.server.common.api;

/**
 * API 에러 코드 계약.
 *
 * <p>클라이언트 분기용 안정 코드와 HTTP status를 묶습니다. 공통 코드는 {@link CommonErrorCode}가, 도메인 코드는 각 도메인 모듈이 자기
 * enum으로 정의해 이 인터페이스를 구현합니다. 코드 규격은 docs/spec/api-response-error-codes.md를 따릅니다.
 *
 * <p>{@code status}는 {@code int}로 둡니다. 그래야 공유 베이스인 {@code common} 모듈이 spring-web에 의존하지 않습니다(HTTP
 * status ↔ 코드 매핑은 렌더링하는 {@code app}의 핸들러가 담당).
 */
public interface ErrorCode {

  /** {@code DOMAIN_REASON} 형식의 {@code UPPER_SNAKE_CASE} 안정 코드. */
  String code();

  /** 이 에러에 매핑되는 HTTP status. */
  int status();

  /** 사람에게 보여줄 수 있는 기본 메시지. 클라이언트가 파싱하지 않습니다. */
  String defaultMessage();
}
