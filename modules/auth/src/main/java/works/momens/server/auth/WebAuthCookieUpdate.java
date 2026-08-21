package works.momens.server.auth;

import java.util.List;

/**
 * 쿠키만 갱신하고 끝나는 웹 인증 결과(세션 갱신·로그아웃).
 *
 * <p>{@code Set-Cookie} 헤더 값을 auth가 완성해서 돌려주므로 표면 모듈은 쿠키 이름·속성(SameSite·Path·TTL)을 알지 못합니다. 표면은 받은
 * 값을 그대로 응답 헤더에 붙이기만 합니다.
 */
public record WebAuthCookieUpdate(List<String> setCookieHeaders) {

  public WebAuthCookieUpdate {
    setCookieHeaders = List.copyOf(setCookieHeaders);
  }
}
