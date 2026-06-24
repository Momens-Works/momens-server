package works.momens.server.user.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import works.momens.server.common.api.BusinessException;
import works.momens.server.support.api.GlobalExceptionHandler;
import works.momens.server.user.UserErrorCode;
import works.momens.server.user.UserService;

/**
 * {@code /me}의 실패 경로가 {@link GlobalExceptionHandler}를 거쳐 Standard 에러 body로 나가는지 검증하는 app 레벨 계약 테스트.
 * 핸들러 일반 매핑은 {@code GlobalExceptionHandlerTest}가, 성공 shape는 user 모듈의 {@code UserControllerTest}가
 * 본다.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MeApiContractTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private UserService userService;

  private final Principal principal = UUID.randomUUID()::toString;

  @Test
  @DisplayName("USER_NOT_FOUND는 404와 Standard 에러 code로 렌더링된다")
  void userNotFoundRendersStandardError() throws Exception {
    when(userService.getProfile(any()))
        .thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

    mockMvc
        .perform(get("/me").principal(principal))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
  }

  @Test
  @DisplayName("@Size 초과 요청은 COMMON_VALIDATION_FAILED와 details.fields로 매핑된다")
  void oversizeFieldFailsValidation() throws Exception {
    String tooLong = "a".repeat(256);

    mockMvc
        .perform(
            patch("/me")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + tooLong + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.details.fields[0].field").value("name"));
  }

  @Test
  @DisplayName("공백 name 요청은 COMMON_VALIDATION_FAILED와 details.fields로 매핑된다")
  void blankNameFailsValidation() throws Exception {
    mockMvc
        .perform(
            patch("/me")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.details.fields[0].field").value("name"));
  }

  @Test
  @DisplayName("Principal이 없으면 AUTH_UNAUTHORIZED로 매핑된다")
  void missingPrincipalFailsAsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
  }

  @Test
  @DisplayName("Principal 이름이 UUID가 아니면 AUTH_INVALID_TOKEN으로 매핑된다")
  void invalidPrincipalNameFailsAsInvalidToken() throws Exception {
    Principal invalidPrincipal = () -> "not-a-uuid";

    mockMvc
        .perform(get("/me").principal(invalidPrincipal))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
  }
}
