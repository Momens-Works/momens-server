package works.momens.server.user.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/** {@code /me} 컨트롤러가 Principal로 현재 사용자를 해석하고 레거시 호환 응답 shape를 내는지 검증합니다. */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private UserService userService;

  private static final UUID USER_ID = UUID.fromString("5d2f7f3a-5db1-4f2c-8b9e-13607dd1f5e8");
  private final Principal principal = USER_ID::toString;

  @Test
  @DisplayName("GET /me는 Principal의 userId로 조회해 {\"user\":{...}} snake_case로 응답한다")
  void getMeReturnsWrappedSnakeCaseProfile() throws Exception {
    when(userService.getProfile(eq(USER_ID)))
        .thenReturn(
            new UserProfile(
                USER_ID,
                "user@example.com",
                "규일",
                "Engineer",
                null,
                Instant.parse("2026-06-24T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z")));

    mockMvc
        .perform(get("/me").principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
        .andExpect(jsonPath("$.user.email").value("user@example.com"))
        .andExpect(jsonPath("$.user.job_role").value("Engineer"))
        .andExpect(jsonPath("$.user.created_at").exists())
        // avatar_url은 null이라 생략된다.
        .andExpect(jsonPath("$.user.avatar_url").doesNotExist());
  }

  @Test
  @DisplayName("PATCH /me는 snake_case 본문을 받아 프로필을 수정한다")
  void patchMeUpdatesProfile() throws Exception {
    when(userService.updateProfile(eq(USER_ID), eq("새이름"), eq("Designer")))
        .thenReturn(
            new UserProfile(
                USER_ID,
                "user@example.com",
                "새이름",
                "Designer",
                null,
                Instant.now(),
                Instant.now()));

    mockMvc
        .perform(
            patch("/me")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"새이름\",\"job_role\":\"Designer\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.name").value("새이름"))
        .andExpect(jsonPath("$.user.job_role").value("Designer"));
  }
}
