package works.momens.server.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import works.momens.server.auth.AuthErrorCode;
import works.momens.server.auth.internal.application.WebAuthService;
import works.momens.server.auth.internal.config.AuthProperties;
import works.momens.server.auth.internal.jwt.TokenPair;
import works.momens.server.auth.internal.web.WebAuthCookies;
import works.momens.server.common.api.BusinessException;

@WebMvcTest(WebAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  WebAuthController.class,
  WebAuthCookies.class,
  WebAuthControllerTest.TestConfig.class,
  WebAuthControllerTest.ApiVersionTestConfig.class
})
class WebAuthControllerTest {

  private static final String API_VERSION_HEADER = "API-Version";
  private static final String API_VERSION = "1";
  private static final String SUCCESS_URI = "http://localhost:3000/auth/success";
  private static final String FAILURE_URI = "http://localhost:3000/auth/failure";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private WebAuthService webAuthService;

  @Test
  void googleLoginRedirectsToConsentAndSetsHandshakeCookies() throws Exception {
    when(webAuthService.startLogin())
        .thenReturn(
            new WebAuthService.LoginRedirect(
                "https://accounts.google.com/o/oauth2/v2/auth?state=state-xyz",
                "state-xyz",
                "verifier-xyz"));

    MvcResult result =
        mockMvc
            .perform(get("/api/auth/google/login").header(API_VERSION_HEADER, API_VERSION))
            .andExpect(status().isFound())
            .andExpect(
                redirectedUrl("https://accounts.google.com/o/oauth2/v2/auth?state=state-xyz"))
            .andReturn();

    List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
    assertThat(setCookies).anyMatch(c -> c.startsWith("oauth_state=state-xyz"));
    assertThat(setCookies).anyMatch(c -> c.startsWith("oauth_pkce_verifier=verifier-xyz"));
    assertThat(setCookies).allMatch(c -> c.contains("HttpOnly"));
  }

  @Test
  void googleCallbackIssuesSessionCookiesAndRedirectsOnSuccess() throws Exception {
    when(webAuthService.completeLogin(eq("auth-code"), eq("verifier-xyz")))
        .thenReturn(new TokenPair("access-jwt", "refresh-token", 900));

    MvcResult result =
        mockMvc
            .perform(
                get("/api/auth/google/callback")
                    .header(API_VERSION_HEADER, API_VERSION)
                    .param("code", "auth-code")
                    .param("state", "state-xyz")
                    .cookie(
                        new Cookie("oauth_state", "state-xyz"),
                        new Cookie("oauth_pkce_verifier", "verifier-xyz")))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl(SUCCESS_URI))
            .andReturn();

    List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
    assertThat(setCookies).anyMatch(c -> c.startsWith("access_token=access-jwt"));
    assertThat(setCookies).anyMatch(c -> c.startsWith("refresh_token=refresh-token"));
    assertThat(setCookies).anyMatch(c -> c.startsWith("oauth_state=") && c.contains("Max-Age=0"));
  }

  @Test
  void googleCallbackRedirectsToFailureOnStateMismatch() throws Exception {
    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .header(API_VERSION_HEADER, API_VERSION)
                .param("code", "auth-code")
                .param("state", "wrong-state")
                .cookie(new Cookie("oauth_state", "state-xyz")))
        .andExpect(status().isFound())
        .andExpect(redirectedUrlPattern(FAILURE_URI + "*error=invalid_state*"));

    verify(webAuthService, never()).completeLogin(any(), any());
  }

  @Test
  void googleCallbackRedirectsToInvalidStateWhenPkceVerifierMissing() throws Exception {
    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .header(API_VERSION_HEADER, API_VERSION)
                .param("code", "auth-code")
                .param("state", "state-xyz")
                .cookie(new Cookie("oauth_state", "state-xyz")))
        .andExpect(status().isFound())
        .andExpect(redirectedUrlPattern(FAILURE_URI + "*error=invalid_state*"));

    verify(webAuthService, never()).completeLogin(any(), any());
  }

  @Test
  void googleCallbackRedirectsToServerErrorOnUnexpectedException() throws Exception {
    when(webAuthService.completeLogin(eq("auth-code"), eq("verifier-xyz")))
        .thenThrow(new IllegalStateException("boom"));

    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .header(API_VERSION_HEADER, API_VERSION)
                .param("code", "auth-code")
                .param("state", "state-xyz")
                .cookie(
                    new Cookie("oauth_state", "state-xyz"),
                    new Cookie("oauth_pkce_verifier", "verifier-xyz")))
        .andExpect(status().isFound())
        .andExpect(redirectedUrlPattern(FAILURE_URI + "*error=server_error*"));
  }

  @Test
  void googleCallbackRedirectsToFailureWhenEmailNotVerified() throws Exception {
    when(webAuthService.completeLogin(eq("auth-code"), eq("verifier-xyz")))
        .thenThrow(new BusinessException(AuthErrorCode.AUTH_GOOGLE_EMAIL_NOT_VERIFIED));

    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .header(API_VERSION_HEADER, API_VERSION)
                .param("code", "auth-code")
                .param("state", "state-xyz")
                .cookie(
                    new Cookie("oauth_state", "state-xyz"),
                    new Cookie("oauth_pkce_verifier", "verifier-xyz")))
        .andExpect(status().isFound())
        .andExpect(redirectedUrlPattern(FAILURE_URI + "*error=email_not_verified*"));
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    AuthProperties authProperties() {
      return new AuthProperties(
          "test-only-momens-auth-jwt-secret-0123456789abcdef",
          Duration.ofMinutes(15),
          Duration.ofDays(14),
          new AuthProperties.Google(
              List.of("test-google-client-id.apps.googleusercontent.com"),
              "https://www.googleapis.com/oauth2/v3/certs"),
          new AuthProperties.Web(
              new AuthProperties.Web.GoogleOauth(
                  "web-client-id",
                  "web-client-secret",
                  "http://localhost:8080/api/auth/google/callback",
                  null,
                  null,
                  null,
                  null),
              new AuthProperties.Web.Cookie(false, "Lax", "access_token", "refresh_token", null),
              new AuthProperties.Web.Redirect(SUCCESS_URI, FAILURE_URI)));
    }
  }

  @TestConfiguration
  static class ApiVersionTestConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
      configurer
          .useRequestHeader(API_VERSION_HEADER)
          .addSupportedVersions(API_VERSION)
          .setVersionRequired(false);
    }
  }
}
