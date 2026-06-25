package works.momens.server.auth.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import works.momens.server.common.api.CommonErrorCode;

/** 인가 거부(403) 본문을 Standard 에러 shape로 내보냅니다. */
@Component
class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final SecurityErrorWriter errorWriter;

  RestAccessDeniedHandler(SecurityErrorWriter errorWriter) {
    this.errorWriter = errorWriter;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    errorWriter.write(response, CommonErrorCode.AUTH_FORBIDDEN);
  }
}
