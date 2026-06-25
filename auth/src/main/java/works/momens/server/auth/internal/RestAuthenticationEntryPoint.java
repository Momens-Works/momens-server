package works.momens.server.auth.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import works.momens.server.common.api.CommonErrorCode;

/** 미인증 요청(401) 본문을 Standard 에러 shape로 내보냅니다. */
@Component
class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final SecurityErrorWriter errorWriter;

  RestAuthenticationEntryPoint(SecurityErrorWriter errorWriter) {
    this.errorWriter = errorWriter;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authenticationException)
      throws IOException {
    errorWriter.write(response, CommonErrorCode.AUTH_UNAUTHORIZED);
  }
}
