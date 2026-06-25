package works.momens.server.auth.internal;

import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import works.momens.server.auth.AuthErrorCode;
import works.momens.server.common.api.BusinessException;

/**
 * 모바일 Google Sign-In ID token 검증.
 *
 * <p>우리 access decoder와 issuer·audience·key source가 다르므로 {@code JwtDecoder} 빈으로 노출하지 않고 이 서비스 내부에
 * 캡슐화합니다(ADR-0004).
 */
@Service
class GoogleIdTokenVerifier {

  private static final Set<String> ALLOWED_ISSUERS =
      Set.of("accounts.google.com", "https://accounts.google.com");

  private final JwtDecoder decoder;

  GoogleIdTokenVerifier(AuthProperties properties) {
    NimbusJwtDecoder jwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(properties.google().jwkSetUri()).build();
    jwtDecoder.setJwtValidator(googleValidator(properties.google().audiences()));
    this.decoder = jwtDecoder;
  }

  GoogleUserInfo verify(String idToken) {
    Jwt jwt;
    try {
      jwt = decoder.decode(idToken);
    } catch (JwtException e) {
      throw new BusinessException(AuthErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
    }

    if (!Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
      throw new BusinessException(AuthErrorCode.AUTH_GOOGLE_EMAIL_NOT_VERIFIED);
    }

    String email = jwt.getClaimAsString("email");
    if (email == null || email.isBlank()) {
      throw new BusinessException(AuthErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
    }

    return new GoogleUserInfo(email, jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
  }

  private static OAuth2TokenValidator<Jwt> googleValidator(Iterable<String> audiences) {
    JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
    return jwt -> {
      OAuth2TokenValidatorResult timestamp = timestampValidator.validate(jwt);
      if (timestamp.hasErrors()) {
        return timestamp;
      }
      if (!ALLOWED_ISSUERS.contains(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())) {
        return invalid("invalid_issuer", "Google ID token issuer is invalid");
      }
      for (String audience : audiences) {
        if (jwt.getAudience().contains(audience)) {
          return OAuth2TokenValidatorResult.success();
        }
      }
      return invalid("invalid_audience", "Google ID token audience is invalid");
    };
  }

  private static OAuth2TokenValidatorResult invalid(String code, String description) {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, description, null));
  }
}
