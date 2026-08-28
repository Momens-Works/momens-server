package works.momens.server.minsu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import works.momens.server.minsu.llm.LlmClient;
import works.momens.server.minsu.llm.LlmRequest;
import works.momens.server.minsu.llm.LlmResponse;
import works.momens.server.minsu.llm.ModelSelection;

/** 다른 Gradle 모듈의 통합 테스트가 Minsu 내부 LLM 타입을 참조하지 않고 provider 응답을 제어하는 fixture. */
public final class MinsuLlmTestFixture {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private volatile Response response;

  public void respondWith(TaskDraft draft) {
    response = new Response(draft, null);
  }

  /** 반환된 latch를 해제할 때까지 provider 응답을 보류한다. */
  public CountDownLatch respondAfterRelease(TaskDraft draft) {
    CountDownLatch release = new CountDownLatch(1);
    response = new Response(draft, release);
    return release;
  }

  LlmResponse generate() {
    Response current = response;
    if (current == null) {
      throw new IllegalStateException("Minsu LLM 테스트 응답을 먼저 설정해야 합니다");
    }
    if (current.release() != null) {
      try {
        if (!current.release().await(20, TimeUnit.SECONDS)) {
          throw new IllegalStateException("provider 해제를 기다리다 시간이 초과됐습니다");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    TaskDraft draft = current.draft();
    return new LlmResponse(
        true, "STOP", responseBody(draft), "test-response-id", LlmResponse.TokenUsage.EMPTY);
  }

  private String responseBody(TaskDraft draft) {
    try {
      return objectMapper.writeValueAsString(
          new DraftResponse(draft.title(), draft.role().value(), draft.priority().value()));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Minsu LLM 테스트 응답 직렬화에 실패했습니다", e);
    }
  }

  private record Response(TaskDraft draft, CountDownLatch release) {}

  private record DraftResponse(String title, String role, String priority) {}

  /** fixture를 사용하는 테스트에서만 명시적으로 import하는 구성. */
  @TestConfiguration(proxyBeanMethods = false)
  public static class Config {

    @Bean
    MinsuLlmTestFixture minsuLlmTestFixture() {
      return new MinsuLlmTestFixture();
    }

    @Bean
    @Primary
    LlmClient testLlmClient(MinsuLlmTestFixture fixture) {
      return new TestLlmClient(fixture);
    }
  }

  private record TestLlmClient(MinsuLlmTestFixture fixture) implements LlmClient {

    @Override
    public LlmResponse generate(ModelSelection selection, LlmRequest request, Duration timeout) {
      return fixture.generate();
    }
  }
}
