package works.momens.server.mobile.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import works.momens.server.mobile.internal.MobileTaskGroup;

/**
 * {@code GET /api/mobile/projects/{projectId}/tasks} 응답. 응답 형식은 docs/spec/mobile-api.md 태스크 절을
 * 따릅니다.
 *
 * <p>제목과 안내 문구는 화면 고정값이고, 그룹은 todo, in_progress, done 순서로 항상 셋을 포함합니다.
 */
@Schema(description = "프로젝트 태스크 보드 응답")
public record TaskBoardResponse(
    @Schema(description = "화면 제목", example = "프로젝트 태스크") String title,
    @Schema(description = "화면 안내 문구") String description,
    @Schema(description = "상태 그룹 목록(todo, in_progress, done 순서)") List<GroupResponse> groups) {

  private static final String BOARD_TITLE = "프로젝트 태스크";
  private static final String BOARD_DESCRIPTION = "업무를 한눈에 확인하고 상세 내용을 확인하세요.";

  @Schema(description = "상태 그룹")
  public record GroupResponse(
      @Schema(description = "그룹 키", example = "todo") String groupKey,
      @Schema(description = "그룹 라벨", example = "투두") String label,
      @Schema(description = "그룹 태스크 수") int count,
      @Schema(description = "그룹 태스크 목록") List<TaskCardResponse> tasks) {}

  @Schema(description = "보드 태스크 카드")
  public record TaskCardResponse(
      @Schema(description = "태스크 식별자") UUID id,
      @Schema(description = "제목") String title,
      @Schema(description = "역할. pm/design/backend/frontend 중 하나", example = "pm") String role,
      @Schema(description = "우선순위. low/medium/high", example = "low") String priority,
      @Schema(description = "관련 자료 수") int materialCount) {}

  public static TaskBoardResponse from(List<MobileTaskGroup> groups) {
    return new TaskBoardResponse(
        BOARD_TITLE, BOARD_DESCRIPTION, groups.stream().map(TaskBoardResponse::toGroup).toList());
  }

  private static GroupResponse toGroup(MobileTaskGroup group) {
    List<TaskCardResponse> cards =
        group.tasks().stream()
            .map(
                card ->
                    new TaskCardResponse(
                        card.id(),
                        card.title(),
                        card.role(),
                        card.priority(),
                        card.materialCount()))
            .toList();
    return new GroupResponse(group.status().key(), group.status().label(), cards.size(), cards);
  }
}
