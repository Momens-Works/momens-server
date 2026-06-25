package works.momens.server.auth.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import works.momens.server.auth.internal.application.AuthService;
import works.momens.server.auth.internal.jwt.TokenPair;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthController.class)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AuthService authService;

  @Test
  void googleTokenExchangeReturnsTokenPair() throws Exception {
    when(authService.loginWithGoogleToken(eq("google-id-token"), eq("iPhone")))
        .thenReturn(new TokenPair("access-token", "refresh-token", 900));

    mockMvc
        .perform(
            post("/api/auth/google/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id_token\":\"google-id-token\",\"device\":\"iPhone\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").value("access-token"))
        .andExpect(jsonPath("$.refresh_token").value("refresh-token"))
        .andExpect(jsonPath("$.token_type").value("Bearer"))
        .andExpect(jsonPath("$.expires_in").value(900));
  }

  @Test
  void refreshReturnsRotatedTokenPair() throws Exception {
    when(authService.refresh(eq("old-refresh-token")))
        .thenReturn(new TokenPair("new-access-token", "new-refresh-token", 900));

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refresh_token\":\"old-refresh-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").value("new-access-token"))
        .andExpect(jsonPath("$.refresh_token").value("new-refresh-token"));
  }

  @Test
  void logoutRevokesRefreshToken() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refresh_token\":\"refresh-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("logged out"));

    verify(authService).logout("refresh-token");
  }
}
