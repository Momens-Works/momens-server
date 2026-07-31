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
  public LlmResponse generate(ModelSelection selection, LlmRequest request) {
    GenerateContentConfig config =
        GenerateContentConfig.builder()
            .candidateCount(1)
            .responseMimeType("application/json")
            .responseSchema(RESPONSE_SCHEMA)
            .systemInstruction(Content.fromParts(Part.fromText(request.systemInstruction())))
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
