package works.momens.server.auth.presentation;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import works.momens.server.auth.internal.application.WebAuthService;
import works.momens.server.auth.internal.config.AuthProperties;
import works.momens.server.auth.internal.jwt.TokenPair;
import works.momens.server.auth.internal.web.WebAuthCookies;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.ErrorCode;

/**
 * 웹 Google 로그인(서버 주도 Authorization Code) 엔드포인트.
 *
 * <p>모바일 JSON 엔드포인트({@code AuthController})와 달리 브라우저 리다이렉트로 동작하며, 토큰은 HttpOnly 쿠키로 전송합니다. 콜백 실패는
 * 브라우저에 JSON을 노출하지 않도록 failure-uri로 리다이렉트합니다(MOM-22).
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class WebAuthController implements WebAuthControllerDocs {

  private final WebAuthService webAuthService;
  private final WebAuthCookies cookies;
  private final AuthProperties properties;

  @Override
  @GetMapping(path = "/google/login", version = "1")
  public void googleLogin(@Parameter(hidden = true) HttpServletResponse response)
      throws IOException {
    String redirectUri;
    try {
      WebAuthService.LoginRedirect redirect = webAuthService.startLogin();
      response.addHeader(HttpHeaders.SET_COOKIE, cookies.state(redirect.state()).toString());
      response.addHeader(
          HttpHeaders.SET_COOKIE, cookies.pkceVerifier(redirect.codeVerifier()).toString());
      redirectUri = redirect.authorizationUrl();
    } catch (RuntimeException e) {
      // 콜백과 동일하게 브라우저에는 기본 에러 대신 failure-uri로 보냅니다.
      log.error("web oauth login start failed", e);
      redirectUri = failureUri("server_error");
    }
    response.sendRedirect(redirectUri);
  }

  @Override
  @GetMapping(path = "/google/callback", version = "1")
  public void googleCallback(
      @RequestParam(name = "code", required = false) String code,
      @RequestParam(name = "state", required = false) String state,
      @Parameter(hidden = true) @CookieValue(name = WebAuthCookies.STATE_COOKIE, required = false)
          String stateCookie,
      @Parameter(hidden = true)
          @CookieValue(name = WebAuthCookies.PKCE_VERIFIER_COOKIE, required = false)
          String codeVerifier,
      @Parameter(hidden = true) HttpServletResponse response)
      throws IOException {
    // 핸드셰이크 쿠키는 결과와 무관하게 정리합니다.
    clearHandshakeCookies(response);
    String redirectUri;
    try {
      TokenPair tokens = webAuthService.completeLogin(code, state, stateCookie, codeVerifier);
      response.addHeader(
          HttpHeaders.SET_COOKIE, cookies.accessToken(tokens.accessToken()).toString());
      response.addHeader(
          HttpHeaders.SET_COOKIE, cookies.refreshToken(tokens.refreshToken()).toString());
      redirectUri = properties.web().redirect().successUri();
    } catch (BusinessException e) {
      log.warn("web oauth callback failed: code={}", e.getErrorCode().code());
      redirectUri = failureUri(failureError(e.getErrorCode()));
    } catch (RuntimeException e) {
      // 외부 호출·발급 중 예기치 못한 예외도 브라우저에 JSON을 노출하지 않고 failure-uri로 보냅니다.
      log.error("web oauth callback failed unexpectedly", e);
      redirectUri = failureUri("server_error");
    }
    response.sendRedirect(redirectUri);
  }

  @Override
  @PostMapping(path = "/web/refresh", version = "1")
  public ResponseEntity<Void> webRefresh(@Parameter(hidden = true) HttpServletRequest request) {
    String refreshToken = cookies.readRefreshToken(request).orElse(null);
    TokenPair tokens = webAuthService.refresh(refreshToken);
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookies.accessToken(tokens.accessToken()).toString());
    headers.add(HttpHeaders.SET_COOKIE, cookies.refreshToken(tokens.refreshToken()).toString());
    return ResponseEntity.noContent().headers(headers).build();
  }

  @Override
  @PostMapping(path = "/web/logout", version = "1")
  public ResponseEntity<Void> webLogout(@Parameter(hidden = true) HttpServletRequest request) {
    cookies
        .readRefreshToken(request)
        .ifPresent(
            refreshToken -> {
              try {
                webAuthService.logout(refreshToken);
              } catch (BusinessException e) {
                // 이미 무효·폐기된 refresh라도 로그아웃은 쿠키를 정리하고 성공으로 끝냅니다(멱등).
                log.debug("web logout with inactive refresh: code={}", e.getErrorCode().code());
              }
            });
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookies.clearAccessToken().toString());
    headers.add(HttpHeaders.SET_COOKIE, cookies.clearRefreshToken().toString());
    return ResponseEntity.noContent().headers(headers).build();
  }

  private void clearHandshakeCookies(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearState().toString());
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearPkceVerifier().toString());
  }

  private String failureUri(String error) {
    return UriComponentsBuilder.fromUriString(properties.web().redirect().failureUri())
        .queryParam("error", error)
        .build()
        .encode()
        .toUriString();
  }

  private static String failureError(ErrorCode errorCode) {
    return switch (errorCode.code()) {
      case "AUTH_OAUTH_STATE_INVALID" -> "invalid_state";
      case "AUTH_GOOGLE_EMAIL_NOT_VERIFIED" -> "email_not_verified";
      case "AUTH_OAUTH_EXCHANGE_FAILED" -> "google_error";
      case "USER_EMAIL_LINKED_TO_ANOTHER_IDENTITY" -> "email_conflict";
      default -> "server_error";
    };
  }
}
