package works.momens.server.auth;

import java.util.List;

/**
 * 브라우저 리다이렉트로 끝나는 웹 인증 결과(로그인 시작·콜백).
 *
 * <p>성공과 실패를 같은 타입으로 돌려줍니다. 실패를 예외로 던지지 않는 이유는 브라우저에 JSON 에러를 노출하지 않고 failure-uri로 보내야 하기
 * 때문이고(MOM-22), 어떤 실패가 어떤 {@code ?error=} 값이 되는지는 auth가 정합니다. 표면 모듈은 받은 쿠키 헤더를 붙이고 {@code
 * redirectUri}로 보내기만 합니다.
 */
public record WebAuthRedirect(List<String> setCookieHeaders, String redirectUri) {

  public WebAuthRedirect {
    setCookieHeaders = List.copyOf(setCookieHeaders);
  }
}
