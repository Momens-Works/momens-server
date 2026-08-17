package works.momens.server.web.workspace;

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
import works.momens.server.web.workspace.dto.response.WorkspaceListResponse;
import works.momens.server.web.workspace.dto.response.WorkspaceResponse;
import works.momens.server.workspace.WorkspaceErrorCode;

/**
 * {@code /api/workspaces} OpenAPI 문서. Swagger 애너테이션을 컨트롤러 구현과 분리합니다(docs/spec/openapi.md).
 *
 * <p>401/403은 보안 필터가 Standard shape로 응답하고, 없는 workspace는 WORKSPACE_NOT_FOUND(404), workspace는 있는데
 * 멤버가 아니면 AUTH_FORBIDDEN(403)입니다.
 */
@Tag(name = "Web", description = "웹 진입 API")
interface WorkspaceControllerDocs {

  @Operation(summary = "워크스페이스 목록 조회", description = "요청자가 멤버인 워크스페이스 목록을 생성 시각 내림차순으로 조회합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "목록 조회 성공. 결과가 없으면 workspaces는 빈 배열입니다.",
      content = @Content(schema = @Schema(implementation = WorkspaceListResponse.class)))
  @ApiExceptions({CommonErrorCode.class})
  WorkspaceListResponse list(Principal principal);

  @Operation(summary = "워크스페이스 상세 조회", description = "워크스페이스 한 건을 조회합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공",
      content = @Content(schema = @Schema(implementation = WorkspaceResponse.class)))
  @ApiExceptions({WorkspaceErrorCode.class, CommonErrorCode.class})
  WorkspaceResponse get(
      @Parameter(description = "워크스페이스 식별자") UUID workspaceId, Principal principal);
}
