package works.momens.server.auth.internal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import works.momens.server.auth.internal.config.validation.ValidJwtSecret;

/**
 * auth 모듈 설정(`momens.auth.*`).
 *
 * <p>access token은 단일 발급자(우리 서버)가 발급·검증하므로 대칭키 HS256을 씁니다. 짧은 access TTL은 변경된 권한의 stale 상한이 되므로(늦어도
 * TTL 안에는 반영) 운영에서 조정할 수 있게 설정으로 둡니다. refresh token은 서버 저장형으로 관리하며(ADR-0005), Google ID token은 별도
 * 검증 서비스가 aud/issuer/JWKS를 검증합니다(ADR-0004).
 *
 * <p>{@code google}은 모바일 ID token 검증(aud/JWKS), {@code web}은 웹 Authorization Code 플로우(code 교환 + 쿠키
 * 전송)를 담습니다(ADR-0003).
 */
@Validated
@ConfigurationProperties("momens.auth")
public record AuthProperties(
    @NotBlank @ValidJwtSecret String jwtSecret,
    @DurationMin(seconds = 1) Duration accessTtl,
    @DurationMin(seconds = 1) Duration refreshTtl,
    @Valid Google google,
    @Valid Web web) {

  public AuthProperties {
    if (accessTtl == null) {
      accessTtl = Duration.ofMinutes(15);
    }
    if (refreshTtl == null) {
      refreshTtl = Duration.ofDays(14);
    }
    if (google == null) {
      google = new Google(List.of(), Google.DEFAULT_JWK_SET_URI);
    }
    if (web == null) {
      web = new Web(null, null, null);
    }
  }

  /** 모바일 Google ID token 검증용(aud allowlist + JWKS). */
  public record Google(@NotEmpty List<@NotBlank String> audiences, @NotBlank String jwkSetUri) {

    static final String DEFAULT_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    public Google {
      audiences = audiences == null ? List.of() : List.copyOf(audiences);
      if (jwkSetUri == null) {
        jwkSetUri = DEFAULT_JWK_SET_URI;
      }
    }
  }

  /** 웹 Authorization Code 플로우 설정(OAuth client + 쿠키 + FE 리다이렉트). */
  public record Web(
      @Valid GoogleOauth googleOauth, @Valid Cookie cookie, @Valid Redirect redirect) {

    public Web {
      if (googleOauth == null) {
        googleOauth = new GoogleOauth(null, null, null, null, null, null, null);
      }
      if (cookie == null) {
        cookie = new Cookie(null, null, null, null, null);
      }
      if (redirect == null) {
        redirect = new Redirect(null, null);
      }
    }

    /** 서버 주도 Authorization Code 교환에 쓰는 Google web client 설정. endpoint는 기본값을 제공합니다. */
    public record GoogleOauth(
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String redirectUri,
        @NotBlank String authorizationUri,
        @NotBlank String tokenUri,
        @NotBlank String userinfoUri,
        @NotEmpty List<@NotBlank String> scopes) {

      static final String DEFAULT_AUTHORIZATION_URI =
          "https://accounts.google.com/o/oauth2/v2/auth";
      static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
      static final String DEFAULT_USERINFO_URI = "https://openidconnect.googleapis.com/v1/userinfo";
      static final List<String> DEFAULT_SCOPES = List.of("openid", "email", "profile");

      public GoogleOauth {
        if (authorizationUri == null) {
          authorizationUri = DEFAULT_AUTHORIZATION_URI;
        }
        if (tokenUri == null) {
          tokenUri = DEFAULT_TOKEN_URI;
        }
        if (userinfoUri == null) {
          userinfoUri = DEFAULT_USERINFO_URI;
        }
        scopes = (scopes == null || scopes.isEmpty()) ? DEFAULT_SCOPES : List.copyOf(scopes);
      }
    }

    /** access/refresh HttpOnly 쿠키 속성. same-domain 배포라 CSRF는 SameSite로 충족합니다(MOM-22). */
    public record Cookie(
        Boolean secure, String sameSite, String accessName, String refreshName, String domain) {

      public Cookie {
        if (secure == null) {
          secure = Boolean.TRUE;
        }
        if (sameSite == null) {
          sameSite = "Lax";
        }
        if (accessName == null) {
          accessName = "access_token";
        }
        if (refreshName == null) {
          refreshName = "refresh_token";
        }
      }
    }

    /** 로그인 성공/실패 후 브라우저를 보낼 momens-fe URL. */
    public record Redirect(@NotBlank String successUri, @NotBlank String failureUri) {}
  }
}
