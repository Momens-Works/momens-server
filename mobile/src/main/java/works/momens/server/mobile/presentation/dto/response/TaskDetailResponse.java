package works.momens.server.mobile.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import works.momens.server.mobile.internal.MobileTaskDetail;

/**
 * {@code GET /api/mobile/tasks/{taskId}} 응답. 응답 형식은 docs/spec/mobile-api.md 태스크 상세 절을 따릅니다.
 *
 * <p>완료 수와 전체 수는 항목 목록에서 파생합니다(별도 저장 없음). {@code materials}는 backing source(source_refs와
 * entity_relations)가 신규 서버에 아직 없어 빈 배열이고, {@code open_questions}와 {@code next_action}은 MVP backing
 * source가 미확정이라 각각 빈 배열, null입니다(명세 합성/파생 필드 응답 정책).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "태스크 상세 응답")
public record TaskDetailResponse(
    @Schema(description = "태스크 식별자") UUID id,
    @Schema(description = "소속 project 식별자") UUID projectId,
    @Schema(description = "제목") String title,
    @Schema(description = "상태. backlog/todo/in_progress/done/cancelled", example = "todo")
        String status,
    @Schema(description = "역할 목록") List<String> roles,
    @Schema(description = "담당자. 미지정이면 null") AssigneeResponse assignee,
    @Schema(description = "우선순위. low/medium/high", example = "medium") String priority,
    @Schema(description = "목적. 작성 전이면 null") String purpose,
    @Schema(description = "완료기준") ChecklistResponse checklist,
    @Schema(description = "관련자료 목록. backing source가 생기기 전까지 빈 배열") List<MaterialResponse> materials,
    @Schema(description = "열린질문 목록. MVP backing source 미확정으로 빈 배열")
        List<OpenQuestionResponse> openQuestions,
    @Schema(description = "다음행동. MVP backing source 미확정으로 null") String nextAction) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @Schema(description = "담당자")
  public record AssigneeResponse(
      @Schema(description = "사용자 식별자") UUID id, @Schema(description = "이름") String name) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @Schema(description = "완료기준")
  public record ChecklistResponse(
      @Schema(description = "완료된 항목 수") int completedCount,
      @Schema(description = "전체 항목 수") int totalCount,
      @Schema(description = "항목 목록(저장 순서)") List<ChecklistItemResponse> items) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @Schema(description = "완료기준 항목")
  public record ChecklistItemResponse(
      @Schema(description = "항목 식별자") UUID id,
      @Schema(description = "항목 제목") String title,
      @Schema(description = "완료 여부") boolean completed) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @Schema(description = "관련자료")
  public record MaterialResponse(
      @Schema(description = "자료 식별자") String id,
      @Schema(description = "제목") String title,
      @Schema(description = "요약") String summary,
      @Schema(description = "역할 목록") List<String> roles,
      @Schema(description = "자료 종류") String kind,
      @Schema(description = "원본 문서 링크") String sourceUrl) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @Schema(description = "열린질문")
  public record OpenQuestionResponse(
      @Schema(description = "질문 식별자") String id, @Schema(description = "질문 본문") String body) {}

  public static TaskDetailResponse from(MobileTaskDetail detail) {
    List<ChecklistItemResponse> items =
        detail.checklistItems().stream()
            .map(item -> new ChecklistItemResponse(item.id(), item.title(), item.completed()))
            .toList();
    int completedCount = (int) items.stream().filter(ChecklistItemResponse::completed).count();
    return new TaskDetailResponse(
        detail.id(),
        detail.projectId(),
        detail.title(),
        detail.status(),
        detail.roles(),
        detail.assignee() == null
            ? null
            : new AssigneeResponse(detail.assignee().id(), detail.assignee().name()),
        detail.priority(),
        detail.purpose(),
        new ChecklistResponse(completedCount, items.size(), items),
        List.of(),
        List.of(),
        null);
  }
}
