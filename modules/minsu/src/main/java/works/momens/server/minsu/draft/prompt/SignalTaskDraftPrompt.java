package works.momens.server.minsu.draft.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;
import works.momens.server.minsu.SignalTaskDraftInput;
import works.momens.server.minsu.draft.json.MinsuJson;
import works.momens.server.minsu.llm.LlmRequest;

@Component
public final class SignalTaskDraftPrompt {

  static final String VERSION = "signal-task-draft-v1";
  static final int MAX_EVIDENCE_COUNT = 10;
  private static final String RESOURCE = "/prompts/signal-task-draft-v1.txt";

  private final MinsuJson json;
  private final String systemInstruction;

  public SignalTaskDraftPrompt(MinsuJson json) {
    this.json = json;
    this.systemInstruction = loadInstruction();
  }

  public LlmRequest render(SignalTaskDraftInput input) {
    List<EvidenceData> evidence =
        input.evidence().stream()
            .map(
                item ->
                    new EvidenceData(
                        trimToNull(item.target()),
                        trimToNull(item.change()),
                        trimToNull(item.impact())))
            .filter(EvidenceData::hasMeaning)
            .limit(MAX_EVIDENCE_COUNT)
            .toList();
    InputData data =
        new InputData(
            trimToNull(input.title()),
            trimToNull(input.type()),
            trimToNull(input.description()),
            trimToNull(input.impact()),
            evidence);
    return new LlmRequest(VERSION, systemInstruction, json.write(data));
  }

  public static boolean hasSufficientContext(SignalTaskDraftInput input) {
    if (hasText(input.description()) || hasText(input.impact())) {
      return true;
    }
    return input.evidence().stream()
        .anyMatch(
            evidence ->
                hasText(evidence.target())
                    || hasText(evidence.change())
                    || hasText(evidence.impact()));
  }

  private static String loadInstruction() {
    try (InputStream input = SignalTaskDraftPrompt.class.getResourceAsStream(RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException("Signal task draft prompt resource를 찾을 수 없습니다");
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Signal task draft prompt resource를 읽을 수 없습니다", e);
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private record InputData(
      String title, String type, String description, String impact, List<EvidenceData> evidence) {}

  private record EvidenceData(String target, String change, String impact) {
    boolean hasMeaning() {
      return target != null || change != null || impact != null;
    }
  }
}
