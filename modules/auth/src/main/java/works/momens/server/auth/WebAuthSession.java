package works.momens.server.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 웹 인증 세션 public API(서버 주도 Authorization Code + HttpOnly 쿠키, ADR-0003).
 *
 * <p>토큰이 아니라 <b>전송이 완성된 결과</b>를 돌려줍니다. 쿠키 속성, 리다이렉트 대상, 실패 시 {@code ?error=} 매핑은 모두 auth가 소유하고, 표면
 * 모듈에는 어떤 쿠키 이름도 노출하지 않습니다.
 *
 * <p>요청에서 쿠키를 읽는 일도 auth가 합니다. refresh 쿠키 이름이 설정값이라 표면이 알 수 없고, 핸드셰이크 쿠키 이름도 auth의 지식이기 때문입니다. 세 값
 * 모두 OpenAPI에 노출되지 않는 파라미터라({@code @Parameter(hidden = true)}) 문서 계약에는 영향이 없습니다.
 */
public interface WebAuthSession {

  /** state·PKCE 쿠키를 만들고 Google consent URL을 돌려줍니다. 시작에 실패하면 failure-uri로 보냅니다. */
  WebAuthRedirect startLogin();

  /**
   * 콜백의 핸드셰이크를 검증하고 code를 교환해 WEB 세션 쿠키를 설정합니다. 핸드셰이크 쿠키는 성공·실패와 무관하게 정리하며, 실패는 failure-uri로 보냅니다.
   */
  WebAuthRedirect completeLogin(HttpServletRequest request, String code, String state);

  /**
   * refresh 쿠키를 회전해 새 access/refresh 쿠키를 돌려줍니다. 쿠키가 없거나 무효면 {@link
   * AuthErrorCode#AUTH_REFRESH_TOKEN_INVALID}를 던져 Standard 에러 body로 응답하게 합니다.
   */
  WebAuthCookieUpdate refresh(HttpServletRequest request);

  /** refresh token을 폐기하고 세션 쿠키를 정리합니다. 쿠키 유무·상태와 무관하게 성공하는 멱등 연산입니다. */
  WebAuthCookieUpdate logout(HttpServletRequest request);
}
