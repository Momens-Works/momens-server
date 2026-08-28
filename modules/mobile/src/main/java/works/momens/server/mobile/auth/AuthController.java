package works.momens.server.mobile.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.auth.AuthTokens;
import works.momens.server.auth.MobileAuthService;
import works.momens.server.mobile.auth.dto.request.GoogleTokenRequest;
import works.momens.server.mobile.auth.dto.request.LogoutRequest;
import works.momens.server.mobile.auth.dto.request.RefreshTokenRequest;
import works.momens.server.mobile.auth.dto.response.AuthMessageResponse;
import works.momens.server.mobile.auth.dto.response.TokenResponse;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController implements AuthControllerDocs {

  private final MobileAuthService mobileAuthService;

  @Override
  @PostMapping(path = "/google/token", version = "1")
  public TokenResponse loginWithGoogleToken(@Valid @RequestBody GoogleTokenRequest request) {
    return toResponse(mobileAuthService.loginWithGoogleToken(request.idToken(), request.device()));
  }

  @Override
  @PostMapping(path = "/refresh", version = "1")
  public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return toResponse(mobileAuthService.refresh(request.refreshToken()));
  }

  @Override
  @PostMapping(path = "/logout", version = "1")
  public AuthMessageResponse logout(@Valid @RequestBody LogoutRequest request) {
    mobileAuthService.logout(request.refreshToken());
    return new AuthMessageResponse("logged out");
  }

  private static TokenResponse toResponse(AuthTokens tokens) {
    return new TokenResponse(
        tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresInSeconds());
  }
}
