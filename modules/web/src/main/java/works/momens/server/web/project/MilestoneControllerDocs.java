package works.momens.server.web.project;

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
import works.momens.server.project.core.ProjectErrorCode;
import works.momens.server.project.task.TaskErrorCode;
import works.momens.server.web.project.dto.request.CreateMilestoneRequest;
import works.momens.server.web.project.dto.response.WebMilestoneResponse;

/**
 * {@code /api/projects/{projectId}/milestones} 엔드포인트의 OpenAPI 문서입니다. Swagger 애너테이션을 컨트롤러 구현과
 * 분리합니다({@code docs/spec/openapi.md}).
 *
 * <p>401 응답은 보안 필터에서 처리합니다. 프로젝트가 존재하지 않으면 {@code PROJECT_NOT_FOUND}(404), 프로젝트는 존재하지만 요청자가 해당
 * 워크스페이스의 멤버가 아니면 {@code AUTH_FORBIDDEN}(403)을 반환합니다. 마일스톤을 생성하려면 member 이상의 권한이 필요합니다.
 */
@Tag(name = "Web", description = "웹 진입 API")
interface MilestoneControllerDocs {

  @Operation(
      operationId = "createMilestone",
      summary = "마일스톤 생성",
      description = "프로젝트에 마일스톤을 생성하고 소유자 목록을 포함해 반환합니다. 소유자를 지정하지 않으면 프로젝트 소유자가 적용됩니다.")
  @ApiResponse(
      responseCode = "201",
      description = "마일스톤 생성 성공",
      content = @Content(schema = @Schema(implementation = WebMilestoneResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, TaskErrorCode.class, CommonErrorCode.class})
  WebMilestoneResponse createMilestone(
      @Parameter(description = "프로젝트 식별자") UUID projectId,
      CreateMilestoneRequest request,
      Principal principal);
}
