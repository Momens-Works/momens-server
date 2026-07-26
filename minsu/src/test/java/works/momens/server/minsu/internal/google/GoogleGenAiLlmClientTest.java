package works.momens.server.minsu.internal.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import works.momens.server.minsu.internal.llm.LlmRequest;
import works.momens.server.minsu.internal.llm.LlmResponse;
import works.momens.server.minsu.internal.llm.ModelSelection;

class GoogleGenAiLlmClientTest {

  private static final ModelSelection SELECTION =
      new ModelSelection("google", "gemini-3.5-flash-lite", "project", "global");
  private static final LlmRequest REQUEST = new LlmRequest("v1", "system", "{}");

  @Test
  void reusesOneSuccessfulClientAcrossConcurrentCallsAndClosesIt() throws Exception {
    AtomicInteger creates = new AtomicInteger();
    FakeGoogleSdkClient sdkClient = new FakeGoogleSdkClient();
    GoogleGenAiLlmClient client =
        new GoogleGenAiLlmClient(
            selection -> {
              creates.incrementAndGet();
              return sdkClient;
            });
    List<Callable<LlmResponse>> calls = new ArrayList<>();
    for (int index = 0; index < 30; index++) {
      calls.add(() -> client.generate(SELECTION, REQUEST));
    }

    try (var executor = Executors.newFixedThreadPool(8)) {
      executor
          .invokeAll(calls)
          .forEach(future -> assertThat(future).succeedsWithin(java.time.Duration.ofSeconds(1)));
    }
    client.close();

    assertThat(creates).hasValue(1);
    assertThat(sdkClient.generates).hasValue(30);
    assertThat(sdkClient.closes).hasValue(1);
  }

  @Test
  void doesNotCacheFailedCreationAndRetriesOnNextRequest() {
    AtomicInteger creates = new AtomicInteger();
    FakeGoogleSdkClient sdkClient = new FakeGoogleSdkClient();
    GoogleGenAiLlmClient client =
        new GoogleGenAiLlmClient(
            selection -> {
              if (creates.incrementAndGet() == 1) {
                throw new IllegalStateException("ADC unavailable");
              }
              return sdkClient;
            });

    assertThatThrownBy(() -> client.generate(SELECTION, REQUEST))
        .isInstanceOf(IllegalStateException.class);
    assertThat(client.generate(SELECTION, REQUEST).candidatePresent()).isTrue();

    assertThat(creates).hasValue(2);
    assertThat(sdkClient.generates).hasValue(1);
  }

  private static final class FakeGoogleSdkClient implements GoogleSdkClient {

    private final AtomicInteger generates = new AtomicInteger();
    private final AtomicInteger closes = new AtomicInteger();

    @Override
    public LlmResponse generate(ModelSelection selection, LlmRequest request) {
      generates.incrementAndGet();
      return new LlmResponse(true, "STOP", "{}", "", LlmResponse.TokenUsage.EMPTY);
    }

    @Override
    public void close() {
      closes.incrementAndGet();
    }
  }
}
