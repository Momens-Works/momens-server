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
import works.momens.server.mobile.presentation.dto.response.BriefResponse;
import works.momens.server.project.ProjectErrorCode;

/**
 * {@code /api/mobile/projects/{projectId}/brief} OpenAPI 문서. Swagger 애너테이션을 컨트롤러 구현과
 * 분리합니다(docs/spec/openapi.md).
 *
 * <p>401/403은 보안 필터가 Standard shape로 응답하고, 없는 project는 PROJECT_NOT_FOUND(404), project는 있는데
 * workspace 멤버가 아니면 AUTH_FORBIDDEN(403)입니다.
 */
@Tag(name = "Mobile", description = "모바일 앱 진입 API")
interface ProjectBriefControllerDocs {

  @Operation(summary = "프로젝트 브리프 조회", description = "브리프 화면(오늘의 브리프)에 필요한 정보를 조회합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "브리프 조회 성공",
      content = @Content(schema = @Schema(implementation = BriefResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  BriefResponse getBrief(
      @Parameter(description = "project 식별자") UUID projectId, Principal principal);
}
