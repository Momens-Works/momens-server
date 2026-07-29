package works.momens.server.minsu;

import java.util.List;

/** Provider와 무관한 Signal task draft 입력 계약. */
public record SignalTaskDraftInput(
    String title, String type, String description, String impact, List<Evidence> evidence) {

  public static final int MAX_EVIDENCE_COUNT = 10;

  public SignalTaskDraftInput {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }

  public record Evidence(String target, String change, String impact) {}
}
