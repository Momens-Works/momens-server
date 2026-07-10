package works.momens.server.mobile.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import works.momens.server.mobile.internal.MobileTaskDetail;

/**
 * {@code GET /api/mobile/tasks/{taskId}} 응답. 응답 형식은 docs/spec/mobile-api.md 태스크 상세 절을 따릅니다.
 *
 * <p>완료 수와 전체 수는 항목 목록에서 파생합니다(별도 저장 없음). {@code materials}는 backing source(source_refs와
 * entity_relations)가 신규 서버에 아직 없어 빈 배열이고, {@code open_questions}와 {@code next_action}은 MVP backing
 * source가 미확정이라 각각 빈 배열, null입니다(명세 합성/파생 필드 응답 정책).
 */
@Schema(description = "태스크 상세 응답")
public record TaskDetailResponse(
    @Schema(description = "태스크 식별자") UUID id,
    @JsonProperty("project_id") @Schema(description = "소속 project 식별자") UUID projectId,
    @Schema(description = "제목") String title,
    @Schema(description = "상태. backlog/todo/in_progress/done/cancelled", example = "todo")
        String status,
    @Schema(description = "역할. pm/design/backend/frontend 중 하나", example = "pm") String role,
    @Schema(description = "담당자. 미지정이면 null") AssigneeResponse assignee,
    @Schema(description = "우선순위. low/medium/high", example = "medium") String priority,
    @Schema(description = "목적. 작성 전이면 null") String purpose,
    @Schema(description = "완료기준") ChecklistResponse checklist,
    @Schema(description = "관련자료 목록. backing source가 생기기 전까지 빈 배열") List<MaterialResponse> materials,
    @JsonProperty("open_questions") @Schema(description = "열린질문 목록. MVP backing source 미확정으로 빈 배열")
        List<OpenQuestionResponse> openQuestions,
    @JsonProperty("next_action") @Schema(description = "다음행동. MVP backing source 미확정으로 null")
        String nextAction) {

  @Schema(description = "담당자")
  public record AssigneeResponse(
      @Schema(description = "사용자 식별자") UUID id,
      @Schema(description = "이름") String name,
      @JsonProperty("avatar_url") @Schema(description = "구글 계정 프로필 이미지. 없으면 null")
          String avatarUrl) {}

  @Schema(description = "완료기준")
  public record ChecklistResponse(
      @JsonProperty("completed_count") @Schema(description = "완료된 항목 수") int completedCount,
      @JsonProperty("total_count") @Schema(description = "전체 항목 수") int totalCount,
      @Schema(description = "항목 목록(저장 순서)") List<ChecklistItemResponse> items) {}

  @Schema(description = "완료기준 항목")
  public record ChecklistItemResponse(
      @Schema(description = "항목 식별자") UUID id,
      @Schema(description = "항목 제목") String title,
      @Schema(description = "완료 여부") boolean completed) {}

  @Schema(description = "관련자료")
  public record MaterialResponse(
      @Schema(description = "자료 식별자") String id,
      @Schema(description = "제목") String title,
      @Schema(description = "요약") String summary,
      @Schema(description = "역할 목록") List<String> roles,
      @Schema(description = "자료 종류") String kind,
      @JsonProperty("source_url") @Schema(description = "원본 문서 링크") String sourceUrl) {}

  @Schema(description = "열린질문")
  public record OpenQuestionResponse(
      @Schema(description = "질문 식별자") String id, @Schema(description = "질문 본문") String body) {}

  public static TaskDetailResponse from(MobileTaskDetail detail) {
    List<ChecklistItemResponse> items =
        detail.checklistItems().stream()
            .map(item -> new ChecklistItemResponse(item.id(), item.title(), item.completed()))
            .toList();
    int completedCount = (int) items.stream().filter(ChecklistItemResponse::completed).count();
    AssigneeResponse assignee = toAssignee(detail.assignee());
    ChecklistResponse checklist = new ChecklistResponse(completedCount, items.size(), items);

    // materials, open_questions, next_action은 backing source가 아직 없어 빈 값으로 내린다(명세 합성 필드 정책).
    return new TaskDetailResponse(
        detail.id(),
        detail.projectId(),
        detail.title(),
        detail.status(),
        detail.role(),
        assignee,
        detail.priority(),
        detail.purpose(),
        checklist,
        List.of(),
        List.of(),
        null);
  }

  private static AssigneeResponse toAssignee(MobileTaskDetail.Assignee assignee) {
    if (assignee == null) {
      return null;
    }
    return new AssigneeResponse(assignee.id(), assignee.name(), assignee.avatarUrl());
  }
}
