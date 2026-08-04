package works.momens.server.minsu.internal.google;

import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import works.momens.server.minsu.internal.llm.LlmRequest;
import works.momens.server.minsu.internal.llm.LlmResponse;
import works.momens.server.minsu.internal.llm.ModelSelection;

final class DefaultGoogleSdkClient implements GoogleSdkClient {

  private static final Schema RESPONSE_SCHEMA = responseSchema();

  private final Client client;

  DefaultGoogleSdkClient(Client client) {
    this.client = client;
  }

  @Override
  public LlmResponse generate(ModelSelection selection, LlmRequest request, Duration timeout) {
    GenerateContentConfig config =
        GenerateContentConfig.builder()
            .candidateCount(1)
            .responseMimeType("application/json")
            .responseSchema(RESPONSE_SCHEMA)
            .systemInstruction(Content.fromParts(Part.fromText(request.systemInstruction())))
            // 요청별 timeout(9.1절). client는 하나를 캐시해 재사용하므로 client-level 값으로는 동기와
            // 비동기가 다른 값을 가질 수 없다. apiVersion·retryOptions까지 포함해 통째로 넘기는 것은
            // 요청 옵션과 client 옵션의 병합 방식에 기대지 않기 위해서다.
            .httpOptions(DefaultGoogleClientFactory.httpOptions(timeout))
            .build();
    GenerateContentResponse response =
        client.models.generateContent(selection.model(), request.dataJson(), config);
    return map(response);
  }

  @Override
  public void close() {
    client.close();
  }

  static LlmResponse map(GenerateContentResponse response) {
    List<Candidate> candidates = response.candidates().orElse(List.of());
    if (candidates.isEmpty()) {
      return new LlmResponse(
          false,
          "",
          "",
          response.responseId().orElse(""),
          tokenUsage(response.usageMetadata().orElse(null)));
    }
    Candidate candidate = candidates.getFirst();
    String text =
        candidate.content().flatMap(Content::parts).orElse(List.of()).stream()
            .map(part -> part.text().orElse(""))
            .collect(Collectors.joining());
    return new LlmResponse(
        true,
        candidate.finishReason().map(Object::toString).orElse(""),
        text,
        response.responseId().orElse(""),
        tokenUsage(response.usageMetadata().orElse(null)));
  }

  static Schema responseSchema() {
    Schema title =
        Schema.builder().type(Type.Known.STRING).description("공백을 포함해 15자 이내인 한국어 실행 항목").build();
    Schema role =
        Schema.builder()
            .type(Type.Known.STRING)
            .enum_("pm", "design", "backend", "frontend")
            .build();
    Schema priority =
        Schema.builder().type(Type.Known.STRING).enum_("low", "medium", "high").build();
    return Schema.builder()
        .type(Type.Known.OBJECT)
        .properties(Map.of("title", title, "role", role, "priority", priority))
        .required("title", "role", "priority")
        .propertyOrdering("title", "role", "priority")
        .build();
  }

  private static LlmResponse.TokenUsage tokenUsage(GenerateContentResponseUsageMetadata usage) {
    if (usage == null) {
      return LlmResponse.TokenUsage.EMPTY;
    }
    return new LlmResponse.TokenUsage(
        usage.promptTokenCount().orElse(0),
        usage.candidatesTokenCount().orElse(0),
        usage.thoughtsTokenCount().orElse(0),
        usage.totalTokenCount().orElse(0));
  }
}
