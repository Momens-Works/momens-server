package works.momens.server.minsu.internal.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import works.momens.server.minsu.Priority;
import works.momens.server.minsu.Role;
import works.momens.server.minsu.SignalTaskDraftInput;
import works.momens.server.minsu.TaskDraft;
import works.momens.server.minsu.internal.config.MinsuConfigStatus;
import works.momens.server.minsu.internal.json.MinsuJson;
import works.momens.server.minsu.internal.llm.LlmClient;
import works.momens.server.minsu.internal.llm.LlmRequest;
import works.momens.server.minsu.internal.llm.LlmResponse;
import works.momens.server.minsu.internal.llm.ModelSelection;
import works.momens.server.minsu.internal.llm.ModelSelectionPolicy;
import works.momens.server.minsu.internal.prompt.SignalTaskDraftPrompt;

class DefaultSignalTaskDraftGeneratorTest {

  private static final ModelSelection SELECTION =
      new ModelSelection("google", "gemini-3.5-flash-lite", "project", "global");

  @Test
  void returnsValidatedStructuredOutput() {
    CapturingClient client =
        responding(success("{\"title\":\"권한 흐름 점검\",\"role\":\"backend\",\"priority\":\"high\"}"));

    TaskDraft result = generator(client, true, true).generate(input());

    assertThat(result).isEqualTo(new TaskDraft("권한 흐름 점검", Role.BACKEND, Priority.HIGH));
    assertThat(client.calls()).isEqualTo(1);
  }

  @Test
  void truncatesTitleWithoutSplittingSurrogatePair() {
    CapturingClient client =
        responding(
            success("{\"title\":\"12345678901234😀끝\",\"role\":\"pm\",\"priority\":\"low\"}"));

    TaskDraft result = generator(client, true, true).generate(input());

    assertThat(result.title()).isEqualTo("12345678901234").hasSizeLessThanOrEqualTo(15);
  }

  @Test
  void usesNormalizedSignalTitleWhenModelTitleIsBlank() {
    CapturingClient client =
        responding(success("{\"title\":\"  \",\"role\":\"design\",\"priority\":\"medium\"}"));
    SignalTaskDraftInput input =
        new SignalTaskDraftInput(" 12345678901234567890 ", "risk", "설명", null, List.of());

    TaskDraft result = generator(client, true, true).generate(input);

    assertThat(result).isEqualTo(new TaskDraft("123456789012345", Role.DESIGN, Priority.MEDIUM));
  }

  @Test
  void recordsTitleFallbackAsGeneratedOutcomeWithoutFallbackReason() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    CapturingClient client =
        responding(success("{\"title\":\" \",\"role\":\"design\",\"priority\":\"medium\"}"));

    generator(client, true, true, ObservationRegistry.create(), meterRegistry).generate(input());

    assertThat(
            meterRegistry
                .get("momens.minsu.task.draft.requests")
                .tag("outcome", "generated_title_fallback")
                .tag("fallback.reason", "none")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @ParameterizedTest
  @MethodSource("invalidEnumResponses")
  void fallsBackWholeDraftForInvalidRoleOrPriority(String response) {
    TaskDraft result = generator(responding(success(response)), true, true).generate(input());

    assertThat(result).isEqualTo(fallback());
  }

  @ParameterizedTest
  @MethodSource("invalidResponses")
  void fallsBackForInvalidProviderResponse(LlmResponse response) {
    TaskDraft result = generator(responding(response), true, true).generate(input());

    assertThat(result).isEqualTo(fallback());
  }

  @ParameterizedTest
  @MethodSource("providerErrors")
  void callsProviderOnceAndFallsBackForProviderErrors(RuntimeException error) {
    CapturingClient client = failing(error);

    TaskDraft result = generator(client, true, true).generate(input());

    assertThat(result).isEqualTo(fallback());
    assertThat(client.calls()).isEqualTo(1);
  }

  @Test
  void doesNotCallProviderWhenDisabled() {
    CapturingClient client = responding(success("{}"));

    TaskDraft result = generator(client, false, true).generate(input());

    assertThat(result).isEqualTo(fallback());
    assertThat(client.calls()).isZero();
  }

  @Test
  void doesNotCallProviderWhenConfigIsInvalid() {
    CapturingClient client = responding(success("{}"));

    TaskDraft result = generator(client, true, false).generate(input());

    assertThat(result).isEqualTo(fallback());
    assertThat(client.calls()).isZero();
  }

  @Test
  void doesNotCallProviderWithoutDescriptionImpactOrEvidence() {
    CapturingClient client = responding(success("{}"));
    SignalTaskDraftInput insufficient =
        new SignalTaskDraftInput("시그널 제목", "risk", " ", null, List.of());

    TaskDraft result = generator(client, true, true).generate(insufficient);

    assertThat(result).isEqualTo(fallback());
    assertThat(client.calls()).isZero();
  }

  @Test
  void createsAndStopsObservationWithLowCardinalityKeysOnSuccess() {
    RecordingObservationHandler handler = new RecordingObservationHandler();
    ObservationRegistry registry = ObservationRegistry.create();
    registry.observationConfig().observationHandler(handler);
    CapturingClient client =
        responding(success("{\"title\":\"점검\",\"role\":\"pm\",\"priority\":\"medium\"}"));

    generator(client, true, true, registry).generate(input());

    assertAll(
        () -> assertThat(handler.started).isEqualTo(1),
        () -> assertThat(handler.stopped).isEqualTo(1),
        () -> assertThat(handler.stoppedContext).isNotNull(),
        () ->
            assertThat(
                    handler.stoppedContext.getLowCardinalityKeyValues().stream()
                        .map(keyValue -> keyValue.getKey() + "=" + keyValue.getValue()))
                .containsExactlyInAnyOrder(
                    "provider=google",
                    "model=gemini-3.5-flash-lite",
                    "outcome=generated",
                    "fallback.reason=none",
                    "finish.reason=stop"));
  }

  @Test
  void stopsObservationAndRecordsErrorWhenProviderThrows() {
    RecordingObservationHandler handler = new RecordingObservationHandler();
    ObservationRegistry registry = ObservationRegistry.create();
    registry.observationConfig().observationHandler(handler);

    generator(failing(new RuntimeException("quota")), true, true, registry).generate(input());

    assertAll(
        () -> assertThat(handler.started).isEqualTo(1),
        () -> assertThat(handler.stopped).isEqualTo(1),
        () -> assertThat(handler.stoppedContext.getError()).isInstanceOf(RuntimeException.class),
        () ->
            assertThat(handler.stoppedContext.getLowCardinalityKeyValues().stream())
                .noneMatch(keyValue -> keyValue.getKey().contains("signal")));
  }

  @Test
  void recordsSafetyFinishReasonAsInvalidResponseWithTokenUsage() {
    RecordingObservationHandler handler = new RecordingObservationHandler();
    ObservationRegistry registry = ObservationRegistry.create();
    registry.observationConfig().observationHandler(handler);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    LlmResponse safetyResponse =
        new LlmResponse(
            true, "SAFETY", "", "response-id", new LlmResponse.TokenUsage(10, 0, 0, 10));

    TaskDraft result =
        generator(responding(safetyResponse), true, true, registry, meterRegistry)
            .generate(input());

    assertAll(
        () -> assertThat(result).isEqualTo(fallback()),
        () ->
            assertThat(
                    meterRegistry
                        .get("momens.minsu.task.draft.requests")
                        .tag("outcome", "fallback")
                        .tag("fallback.reason", "invalid_response")
                        .counter()
                        .count())
                .isEqualTo(1),
        () ->
            assertThat(
                    meterRegistry
                        .get("momens.minsu.llm.tokens")
                        .tag("type", "prompt")
                        .summary()
                        .totalAmount())
                .isEqualTo(10),
        () ->
            assertThat(
                    handler.stoppedContext.getLowCardinalityKeyValues().stream()
                        .map(keyValue -> keyValue.getKey() + "=" + keyValue.getValue()))
                .contains(
                    "outcome=fallback",
                    "fallback.reason=invalid_response",
                    "finish.reason=safety"));
  }

  private DefaultSignalTaskDraftGenerator generator(
      LlmClient client, boolean enabled, boolean valid) {
    return generator(client, enabled, valid, ObservationRegistry.create());
  }

  private DefaultSignalTaskDraftGenerator generator(
      LlmClient client, boolean enabled, boolean valid, ObservationRegistry observationRegistry) {
    return generator(client, enabled, valid, observationRegistry, new SimpleMeterRegistry());
  }

  private DefaultSignalTaskDraftGenerator generator(
      LlmClient client,
      boolean enabled,
      boolean valid,
      ObservationRegistry observationRegistry,
      SimpleMeterRegistry meterRegistry) {
    MinsuConfigStatus config = mock(MinsuConfigStatus.class);
    when(config.enabled()).thenReturn(enabled);
    when(config.valid()).thenReturn(valid);
    ModelSelectionPolicy selectionPolicy = useCase -> SELECTION;
    MinsuJson json = new MinsuJson();
    return new DefaultSignalTaskDraftGenerator(
        config,
        selectionPolicy,
        client,
        new SignalTaskDraftPrompt(json),
        json,
        new MinsuObservability(meterRegistry, observationRegistry));
  }

  private static SignalTaskDraftInput input() {
    return new SignalTaskDraftInput(
        "시그널 제목",
        "risk",
        "사용자가 반복해서 이탈합니다",
        null,
        List.of(new SignalTaskDraftInput.Evidence("권한 화면", "이탈 증가", "완료율 하락")));
  }

  private static TaskDraft fallback() {
    return new TaskDraft("시그널 제목", Role.PM, Priority.MEDIUM);
  }

  private static LlmResponse success(String text) {
    return new LlmResponse(
        true, "STOP", text, "response-id", new LlmResponse.TokenUsage(10, 5, 1, 16));
  }

  private static CapturingClient responding(LlmResponse response) {
    return new CapturingClient(response, null);
  }

  private static CapturingClient failing(RuntimeException error) {
    return new CapturingClient(null, error);
  }

  private static Stream<Arguments> invalidEnumResponses() {
    return Stream.of(
        Arguments.of("{\"title\":\"점검\",\"role\":\"invalid\",\"priority\":\"high\"}"),
        Arguments.of("{\"title\":\"점검\",\"role\":\"pm\",\"priority\":\"urgent\"}"),
        Arguments.of("{\"title\":\"점검\",\"role\":\"\",\"priority\":\"medium\"}"),
        Arguments.of("{\"title\":\"점검\",\"role\":\"pm\",\"priority\":\"\"}"));
  }

  private static Stream<LlmResponse> invalidResponses() {
    return Stream.of(
        new LlmResponse(false, "", null, "", LlmResponse.TokenUsage.EMPTY),
        new LlmResponse(true, "MAX_TOKENS", "{}", "", LlmResponse.TokenUsage.EMPTY),
        new LlmResponse(true, "STOP", " ", "", LlmResponse.TokenUsage.EMPTY),
        new LlmResponse(true, "STOP", "{malformed", "", LlmResponse.TokenUsage.EMPTY));
  }

  private static Stream<RuntimeException> providerErrors() {
    return Stream.of(
        new RuntimeException("provider"),
        new RuntimeException("rate limit"),
        new RuntimeException("quota"));
  }

  private static final class CapturingClient implements LlmClient {

    private final LlmResponse response;
    private final RuntimeException error;
    private final AtomicInteger calls = new AtomicInteger();

    private CapturingClient(LlmResponse response, RuntimeException error) {
      this.response = response;
      this.error = error;
    }

    @Override
    public LlmResponse generate(ModelSelection selection, LlmRequest request) {
      calls.incrementAndGet();
      if (error != null) {
        throw error;
      }
      return response;
    }

    int calls() {
      return calls.get();
    }
  }

  private static final class RecordingObservationHandler
      implements ObservationHandler<Observation.Context> {

    private int started;
    private int stopped;
    private Observation.Context stoppedContext;

    @Override
    public void onStart(Observation.Context context) {
      started++;
    }

    @Override
    public void onStop(Observation.Context context) {
      stopped++;
      stoppedContext = context;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
      return true;
    }
  }
}
