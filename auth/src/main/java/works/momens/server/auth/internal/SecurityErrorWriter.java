package works.momens.server.auth.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import works.momens.server.common.api.ErrorCode;
import works.momens.server.common.api.ErrorResponse;

/**
 * SecurityFilterChain 단계의 인증/인가 거부 본문을 Standard 에러 shape로 직렬화합니다.
 *
 * <p>필터 단계 거부(401/403)는 {@code @RestControllerAdvice}에 닿지 않으므로, 전역 핸들러와 같은 본문 계약을 여기서 별도로 내보냅니다(공유
 * {@link ErrorResponse}).
 */
@Component
class SecurityErrorWriter {

  private final ObjectMapper objectMapper;

  // 이 앱은 ObjectMapper를 빈으로 노출하지 않습니다(MVC가 컨버터 내부 매퍼로 직렬화). 빈이 있으면 그걸,
  // 없으면 기본 매퍼를 씁니다. 에러 본문 키는 단순하고 null 생략은 @JsonInclude가 처리하므로 안전합니다.
  SecurityErrorWriter(ObjectProvider<ObjectMapper> objectMapperProvider) {
    this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
  }

  void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    response.setStatus(errorCode.status());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(
        response.getWriter(), ErrorResponse.of(errorCode.code(), errorCode.defaultMessage(), null));
  }
}
