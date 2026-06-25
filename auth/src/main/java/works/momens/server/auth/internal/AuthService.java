package works.momens.server.auth.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/** 모바일 로그인과 refresh/logout use case orchestration. */
@Service
@RequiredArgsConstructor
public class AuthService {

  private final GoogleIdTokenVerifier googleIdTokenVerifier;
  private final UserService userService;
  private final JwtTokenService jwtTokenService;

  @Transactional
  public TokenPair loginWithGoogleToken(String idToken, String device) {
    GoogleUserInfo googleUser = googleIdTokenVerifier.verify(idToken);
    UserProfile user =
        userService.findOrCreate(googleUser.email(), displayName(googleUser), googleUser.picture());
    return jwtTokenService.issueTokenPair(user.id(), ClientType.MOBILE, device);
  }

  public TokenPair refresh(String refreshToken) {
    return jwtTokenService.refresh(refreshToken);
  }

  public void logout(String refreshToken) {
    jwtTokenService.revoke(refreshToken);
  }

  private static String displayName(GoogleUserInfo googleUser) {
    if (googleUser.name() == null || googleUser.name().isBlank()) {
      return googleUser.email();
    }
    return googleUser.name();
  }
}
