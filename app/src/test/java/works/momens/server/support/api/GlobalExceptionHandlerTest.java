package works.momens.server.support.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.ErrorCode;

/** 전역 예외 핸들러가 예외를 Standard 에러 응답으로 매핑하는지 검증합니다. */
@WebMvcTest
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("BusinessException은 ErrorCode의 status·code·message와 details로 렌더링된다")
  void rendersBusinessException() throws Exception {
    mockMvc
        .perform(get("/test/business"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TEST_NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("테스트 리소스를 찾을 수 없습니다."))
        .andExpect(jsonPath("$.error.details.resourceId").value("abc"));
  }

  @Test
  @DisplayName("details가 없으면 error.details 필드를 생략한다")
  void omitsDetailsWhenAbsent() throws Exception {
    mockMvc
        .perform(get("/test/business-default"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TEST_NOT_FOUND"))
        .andExpect(jsonPath("$.error.details").doesNotExist());
  }

  @Test
  @DisplayName("Bean Validation 실패는 COMMON_VALIDATION_FAILED와 details.fields로 매핑된다")
  void rendersValidationFailure() throws Exception {
    mockMvc
        .perform(
            post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.details.fields[0].field").value("name"))
        .andExpect(jsonPath("$.error.details.fields[0].reason").exists());
  }

  @Test
  @DisplayName("잘못된 JSON은 COMMON_BAD_REQUEST로 매핑된다")
  void rendersBadRequestForMalformedJson() throws Exception {
    mockMvc
        .perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_BAD_REQUEST"));
  }

  @Test
  @DisplayName("파라미터 타입 불일치는 COMMON_BAD_REQUEST로 매핑된다")
  void rendersBadRequestForTypeMismatch() throws Exception {
    mockMvc
        .perform(get("/test/typed").param("value", "not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_BAD_REQUEST"));
  }

  @Test
  @DisplayName("예기치 못한 예외는 500으로 매핑되고 내부 메시지를 노출하지 않는다")
  void rendersInternalServerErrorWithoutLeakingMessage() throws Exception {
    mockMvc
        .perform(get("/test/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value("COMMON_INTERNAL_SERVER_ERROR"))
        .andExpect(jsonPath("$.error.message").value("서버 내부 오류가 발생했습니다."))
        .andExpect(content().string(not(containsString("boom secret"))));
  }

  @RestController
  static class TestController {

    @GetMapping("/test/business")
    void business() {
      throw new BusinessException(TestErrorCode.TEST_NOT_FOUND, Map.of("resourceId", "abc"));
    }

    @GetMapping("/test/business-default")
    void businessDefault() {
      throw new BusinessException(TestErrorCode.TEST_NOT_FOUND);
    }

    @PostMapping("/test/validate")
    void validate(@Valid @RequestBody TestRequest request) {}

    @GetMapping("/test/typed")
    void typed(@RequestParam int value) {}

    @GetMapping("/test/boom")
    void boom() {
      throw new IllegalStateException("boom secret should not leak");
    }

    record TestRequest(@NotBlank String name) {}
  }

  @RequiredArgsConstructor
  enum TestErrorCode implements ErrorCode {
    TEST_NOT_FOUND(404, "테스트 리소스를 찾을 수 없습니다.");

    private final int status;
    private final String defaultMessage;

    @Override
    public String code() {
      return name();
    }

    @Override
    public int status() {
      return status;
    }

    @Override
    public String defaultMessage() {
      return defaultMessage;
    }
  }
}
