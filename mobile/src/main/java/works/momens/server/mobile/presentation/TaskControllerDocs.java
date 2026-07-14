package works.momens.server.mobile.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.UUID;
import works.momens.server.common.api.ApiExceptions;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.mobile.presentation.dto.request.ToggleChecklistItemRequest;
import works.momens.server.mobile.presentation.dto.request.UpdateTaskRequest;
import works.momens.server.mobile.presentation.dto.response.ChecklistToggleResponse;
import works.momens.server.mobile.presentation.dto.response.TaskDetailResponse;
import works.momens.server.project.ProjectErrorCode;

/**
 * {@code /api/mobile/tasks/{taskId}} OpenAPI 문서. Swagger 애너테이션을 컨트롤러 구현과
 * 분리합니다(docs/spec/openapi.md).
 *
 * <p>401/403은 보안 필터가 Standard 형식으로 응답하고, 없거나 삭제된 태스크는 TASK_NOT_FOUND(404), 태스크는 있는데 workspace 멤버가
 * 아니면 AUTH_FORBIDDEN(403)입니다.
 */
@Tag(name = "Mobile", description = "모바일 앱 진입 API")
interface TaskControllerDocs {

  @Operation(
      summary = "태스크 상세 조회",
      description =
          "태스크 상세 화면에 필요한 정보를 조회합니다. materials, open_questions, next_action은 backing source가 생기기 전까지"
              + " 각각 빈 배열, 빈 배열, null입니다.")
  @ApiResponse(
      responseCode = "200",
      description = "상세 조회 성공. 담당자 미지정이면 assignee가, 목적 작성 전이면 purpose가 null입니다.",
      content = @Content(schema = @Schema(implementation = TaskDetailResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  TaskDetailResponse getTaskDetail(
      @Parameter(description = "task 식별자") UUID taskId, Principal principal);

  @Operation(
      summary = "태스크 수정",
      description =
          "수정 화면이 저장한 편집 상태 전체로 태스크를 갱신합니다. 제목, 역할, 담당자, 우선순위, 상태, 목적, 완료기준을 한 번에 저장합니다. 담당자를"
              + " 비우려면 assignee_id를 null로 보내고, 완료기준은 최종 목록으로 전체 교체하며 0개에서 5개까지 허용합니다. 저장만 하고 본문은"
              + " 반환하지 않으므로, 저장 후 최신 상태는 태스크 상세 조회로 다시 읽습니다.")
  @ApiResponse(responseCode = "204", description = "수정 성공. 본문 없음. 최신 상태는 상세 조회로 읽습니다.")
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  void updateTask(
      @Parameter(description = "task 식별자") UUID taskId,
      UpdateTaskRequest request,
      Principal principal);

  @Operation(
      summary = "완료기준 완료 상태 변경",
      description = "완료기준 항목 하나의 완료 상태를 변경합니다. 변경한 항목과 함께 태스크 전체 완료 수, 전체 수를 반환합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "변경 성공",
      content = @Content(schema = @Schema(implementation = ChecklistToggleResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  ChecklistToggleResponse toggleChecklistItem(
      @Parameter(description = "task 식별자") UUID taskId,
      @Parameter(description = "완료기준 항목 식별자") UUID itemId,
      ToggleChecklistItemRequest request,
      Principal principal);
}
