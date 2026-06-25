package works.momens.server.auth.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.auth.internal.application.AuthService;

@RestController
@RequiredArgsConstructor
class AuthController implements AuthControllerDocs {

  private final AuthService authService;

  @Override
  @PostMapping(path = "/api/auth/google/token", version = "1")
  public TokenResponse loginWithGoogleToken(@Valid @RequestBody GoogleTokenRequest request) {
    return TokenResponse.from(
        authService.loginWithGoogleToken(request.idToken(), request.device()));
  }

  @Override
  @PostMapping(path = "/api/auth/refresh", version = "1")
  public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return TokenResponse.from(authService.refresh(request.refreshToken()));
  }

  @Override
  @PostMapping(path = "/api/auth/logout", version = "1")
  public AuthMessageResponse logout(@Valid @RequestBody LogoutRequest request) {
    authService.logout(request.refreshToken());
    return new AuthMessageResponse("logged out");
  }
}
