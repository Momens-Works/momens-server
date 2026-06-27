package works.momens.server.auth.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 웹 Google 로그인(Authorization Code) OpenAPI 문서.
 *
 * <p>두 엔드포인트 모두 브라우저 리다이렉트(302)라 JSON 본문이 없습니다. 실패도 JSON 대신 failure-uri로 리다이렉트하므로 표준 에러 응답
 * (@ApiExceptions)을 쓰지 않습니다.
 */
@Tag(name = "Auth", description = "인증 API")
interface WebAuthControllerDocs {

  @Operation(
      summary = "웹 Google 로그인 시작",
      description = "state·PKCE 쿠키를 설정하고 Google consent 화면으로 리다이렉트합니다.")
  @ApiResponse(responseCode = "302", description = "Google consent로 리다이렉트")
  void googleLogin(HttpServletResponse response) throws IOException;

  @Operation(
      summary = "웹 Google 로그인 콜백",
      description =
          "code를 교환해 WEB 세션 토큰을 발급합니다. 성공: access/refresh HttpOnly 쿠키 설정 후 success-uri로"
              + " 리다이렉트. 실패: failure-uri로 리다이렉트하며 `?error=`에 invalid_state |"
              + " email_not_verified | google_error | server_error 중 하나를 싣습니다.")
  @ApiResponse(responseCode = "302", description = "성공/실패 모두 리다이렉트")
  void googleCallback(
      String code,
      String state,
      String stateCookie,
      String codeVerifier,
      HttpServletResponse response)
      throws IOException;
}
