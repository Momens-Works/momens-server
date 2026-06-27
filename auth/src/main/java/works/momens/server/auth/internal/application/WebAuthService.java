package works.momens.server.auth.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import works.momens.server.auth.internal.google.GoogleOAuthClient;
import works.momens.server.auth.internal.google.GoogleUserInfo;
import works.momens.server.auth.internal.jwt.JwtTokenService;
import works.momens.server.auth.internal.jwt.TokenPair;
import works.momens.server.auth.internal.refresh.ClientType;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/**
 * 웹 Authorization Code 로그인 use case. 모바일과 같은 코어(user upsert + 토큰 발급)를 공유하되 서버 주도 code 교환과
 * state/PKCE 핸드셰이크가 필요해 분리하고, {@link ClientType#WEB}으로 발급합니다(ADR-0003).
 */
@Service
@RequiredArgsConstructor
public class WebAuthService {

  private static final int TOKEN_BYTES = 32;

  private final GoogleOAuthClient googleOAuthClient;
  private final UserService userService;
  private final JwtTokenService jwtTokenService;
  private final SecureRandom secureRandom = new SecureRandom();

  /** state·PKCE를 생성하고 consent로 보낼 authorization URL을 만듭니다. */
  public LoginRedirect startLogin() {
    String state = randomToken();
    String codeVerifier = randomToken();
    String codeChallenge = codeChallenge(codeVerifier);
    String authorizationUrl = googleOAuthClient.authorizationUrl(state, codeChallenge);
    return new LoginRedirect(authorizationUrl, state, codeVerifier);
  }

  /** 콜백의 code를 교환·검증하고 WEB 세션 토큰을 발급합니다. */
  public TokenPair completeLogin(String code, String codeVerifier) {
    String googleAccessToken = googleOAuthClient.exchangeCode(code, codeVerifier);
    GoogleUserInfo user = googleOAuthClient.fetchUserInfo(googleAccessToken);
    UserProfile profile = userService.findOrCreate(user.email(), displayName(user), user.picture());
    return jwtTokenService.issueTokenPair(profile.id(), ClientType.WEB, null);
  }

  private String randomToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String codeChallenge(String codeVerifier) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm is not available", e);
    }
  }

  private static String displayName(GoogleUserInfo user) {
    if (user.name() == null || user.name().isBlank()) {
      return user.email();
    }
    return user.name();
  }

  /** {@link #startLogin()} 결과: consent URL과 콜백 검증에 필요한 state·PKCE verifier. */
  public record LoginRedirect(String authorizationUrl, String state, String codeVerifier) {}
}
