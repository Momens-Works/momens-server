package works.momens.server.auth;

/**
 * 발급된 세션 토큰 쌍의 모듈 경계 표현.
 *
 * <p>내부 발급 타입({@code internal.jwt.TokenPair})과 분리된 public API 반환 타입입니다. 표면 모듈은 이 값을 응답 shape로
 * 매핑합니다.
 *
 * <p>웹은 토큰을 HttpOnly 쿠키로만 전송하므로(ADR-0003) 이 타입을 쓰지 않습니다. 웹 흐름의 결과는 {@link WebAuthSession}이 완성된
 * {@code Set-Cookie} 헤더로 돌려줍니다.
 */
public record AuthTokens(String accessToken, String refreshToken, long expiresInSeconds) {}
