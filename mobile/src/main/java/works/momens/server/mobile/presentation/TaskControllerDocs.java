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
}
