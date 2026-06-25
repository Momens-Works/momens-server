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
  @PostMapping("/api/auth/google/token")
  public TokenResponse loginWithGoogleToken(@Valid @RequestBody GoogleTokenRequest request) {
    return TokenResponse.from(
        authService.loginWithGoogleToken(request.idToken(), request.device()));
  }

  @Override
  @PostMapping("/api/auth/refresh")
  public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return TokenResponse.from(authService.refresh(request.refreshToken()));
  }

  @Override
  @PostMapping("/api/auth/logout")
  public AuthMessageResponse logout(@Valid @RequestBody LogoutRequest request) {
    authService.logout(request.refreshToken());
    return new AuthMessageResponse("logged out");
  }
}
