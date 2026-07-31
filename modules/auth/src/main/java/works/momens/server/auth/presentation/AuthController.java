package works.momens.server.auth.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.auth.internal.application.AuthService;
import works.momens.server.auth.internal.jwt.TokenPair;
import works.momens.server.auth.presentation.dto.request.GoogleTokenRequest;
import works.momens.server.auth.presentation.dto.request.LogoutRequest;
import works.momens.server.auth.presentation.dto.request.RefreshTokenRequest;
import works.momens.server.auth.presentation.dto.response.AuthMessageResponse;
import works.momens.server.auth.presentation.dto.response.TokenResponse;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController implements AuthControllerDocs {

  private final AuthService authService;

  @Override
  @PostMapping(path = "/google/token", version = "1")
  public TokenResponse loginWithGoogleToken(@Valid @RequestBody GoogleTokenRequest request) {
    return toResponse(authService.loginWithGoogleToken(request.idToken(), request.device()));
  }

  @Override
  @PostMapping(path = "/refresh", version = "1")
  public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return toResponse(authService.refresh(request.refreshToken()));
  }

  @Override
  @PostMapping(path = "/logout", version = "1")
  public AuthMessageResponse logout(@Valid @RequestBody LogoutRequest request) {
    authService.logout(request.refreshToken());
    return new AuthMessageResponse("logged out");
  }

  private static TokenResponse toResponse(TokenPair tokenPair) {
    return new TokenResponse(
        tokenPair.accessToken(), tokenPair.refreshToken(), "Bearer", tokenPair.expiresInSeconds());
  }
}
