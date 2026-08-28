package works.momens.server.mobile.auth;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import works.momens.server.auth.AuthTokens;
import works.momens.server.auth.MobileAuthService;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthControllerTest.ApiVersionTestConfig.class)
class AuthControllerTest {

  private static final String API_VERSION_HEADER = "API-Version";
  private static final String API_VERSION = "1";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private MobileAuthService mobileAuthService;

  @Test
  @DisplayName("Google ID 토큰 교환은 access·refresh 토큰을 snake_case로 응답한다")
  void googleTokenExchangeReturnsTokenPair() throws Exception {
    when(mobileAuthService.loginWithGoogleToken(eq("google-id-token"), eq("iPhone")))
        .thenReturn(new AuthTokens("access-token", "refresh-token", 900));

    mockMvc
        .perform(
            post("/api/auth/google/token")
                .header(API_VERSION_HEADER, API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id_token\":\"google-id-token\",\"device\":\"iPhone\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").value("access-token"))
        .andExpect(jsonPath("$.refresh_token").value("refresh-token"))
        .andExpect(jsonPath("$.token_type").value("Bearer"))
        .andExpect(jsonPath("$.expires_in").value(900));
  }

  @Test
  @DisplayName("API-Version 헤더가 없으면 v1으로 라우팅한다")
  void googleTokenExchangeAcceptsRequestWithoutApiVersionHeader() throws Exception {
    when(mobileAuthService.loginWithGoogleToken(eq("google-id-token"), eq("iPhone")))
        .thenReturn(new AuthTokens("access-token", "refresh-token", 900));

    mockMvc
        .perform(
            post("/api/auth/google/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id_token\":\"google-id-token\",\"device\":\"iPhone\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("지원하지 않는 API-Version은 거부한다")
  void googleTokenExchangeRejectsUnsupportedApiVersion() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/google/token")
                .header(API_VERSION_HEADER, "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id_token\":\"google-id-token\",\"device\":\"iPhone\"}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @DisplayName("재발급은 회전된 access·refresh 토큰을 응답한다")
  void refreshReturnsRotatedTokenPair() throws Exception {
    when(mobileAuthService.refresh(eq("old-refresh-token")))
        .thenReturn(new AuthTokens("new-access-token", "new-refresh-token", 900));

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .header(API_VERSION_HEADER, API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refresh_token\":\"old-refresh-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").value("new-access-token"))
        .andExpect(jsonPath("$.refresh_token").value("new-refresh-token"));
  }

  @Test
  @DisplayName("로그아웃은 refresh token을 폐기한다")
  void logoutRevokesRefreshToken() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/logout")
                .header(API_VERSION_HEADER, API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refresh_token\":\"refresh-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("logged out"));

    verify(mobileAuthService).logout("refresh-token");
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
