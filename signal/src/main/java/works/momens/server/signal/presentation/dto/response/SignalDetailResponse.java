package works.momens.server.signal.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import works.momens.server.signal.SignalActionService;
import works.momens.server.signal.SignalDetail;

/**
 * {@code GET /api/mobile/signals/{signalId}} 응답. shape는 docs/spec/mobile-api.md의 시그널 상세 절을 따릅니다.
 *
 * <p>상단 라벨은 {@code type}에서 앱이 파생하므로 처리 상태 필드는 없습니다. {@code impact}, {@code minsu.suggestion}은
 * worker/Minsu 미생산 시 null입니다. {@code minsu.task_draft}는 산출물이 없으면 서버가 Signal 제목 기반 최소 초안(roles [],
 * priority medium)을 내려줍니다. {@code fields}는 MVP에서 항상 빈 목록입니다(근거 없는 값 미생성 정책). action은 미처리 Signal 기준
 * 고정 목록입니다.
 */
@Schema(description = "시그널 상세 응답")
public record SignalDetailResponse(
    UUID id,
    ProjectRef project,
    String type,
    String title,
    String description,
    @Schema(nullable = true) String impact,
    List<EvidenceResponse> evidence,
    Minsu minsu,
    List<String> actions,
    @JsonProperty("primary_action") String primaryAction) {

  private static final List<String> ACTIONS = List.of("convert-to-task", "dismiss");
  private static final String PRIMARY_ACTION = "convert-to-task";

  public record ProjectRef(UUID id, @Schema(nullable = true) String name) {}

  public record EvidenceResponse(
      UUID id,
      String source,
      @JsonProperty("source_title") @Schema(nullable = true) String sourceTitle,
      @JsonProperty("occurred_at") @Schema(nullable = true) Instant occurredAt,
      @Schema(nullable = true) String summary,
      @Schema(description = "provider별 부가 필드. MVP에서는 항상 빈 목록입니다.") List<FieldResponse> fields,
      @JsonProperty("source_url") @Schema(nullable = true) String sourceUrl) {}

  public record FieldResponse(String label, String value) {}

  public record Minsu(
      @Schema(nullable = true) String suggestion,
      @JsonProperty("task_draft") TaskDraft taskDraft) {}

  public record TaskDraft(String title, List<String> roles, String priority) {}

  public static SignalDetailResponse from(SignalDetail detail) {
    return new SignalDetailResponse(
        detail.id(),
        toProject(detail),
        detail.type(),
        detail.title(),
        detail.description(),
        detail.impact(),
        toEvidenceList(detail),
        toMinsu(detail),
        ACTIONS,
        PRIMARY_ACTION);
  }

  private static ProjectRef toProject(SignalDetail detail) {
    return new ProjectRef(detail.projectId(), detail.projectName());
  }

  private static List<EvidenceResponse> toEvidenceList(SignalDetail detail) {
    return detail.evidence().stream().map(SignalDetailResponse::toEvidence).toList();
  }

  private static EvidenceResponse toEvidence(SignalDetail.Evidence evidence) {
    return new EvidenceResponse(
        evidence.id(),
        evidence.source(),
        evidence.sourceTitle(),
        evidence.occurredAt(),
        evidence.summary(),
        List.of(),
        evidence.sourceUrl());
  }

  private static Minsu toMinsu(SignalDetail detail) {
    return new Minsu(detail.minsuSuggestion(), minimalTaskDraft(detail.title()));
  }

  /** worker/Minsu 산출물이 없을 때 서버가 제공하는 Signal 제목 기반 최소 초안. */
  private static TaskDraft minimalTaskDraft(String signalTitle) {
    return new TaskDraft(
        signalTitle, List.of(), SignalActionService.ConvertToTaskCommand.DEFAULT_PRIORITY);
  }
}
