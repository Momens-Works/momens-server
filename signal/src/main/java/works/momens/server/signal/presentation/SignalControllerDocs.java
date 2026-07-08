package works.momens.server.signal.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.UUID;
import works.momens.server.common.api.ApiExceptions;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.signal.presentation.dto.response.SignalListResponse;

/**
 * {@code /api/mobile/projects/{projectId}/signals} OpenAPI 문서. Swagger 애너테이션을 컨트롤러 구현과
 * 분리합니다(docs/spec/openapi.md).
 *
 * <p>401/403은 보안 필터가 Standard shape로 응답하고, 없는 project는 PROJECT_NOT_FOUND(404), project는 있는데
 * workspace 멤버가 아니면 AUTH_FORBIDDEN(403)입니다.
 */
@Tag(name = "Signal", description = "모바일 Signal API")
interface SignalControllerDocs {

  @Operation(
      summary = "시그널 목록 조회",
      description =
          "프로젝트의 아직 처리되지 않은 시그널을 생성 시각 내림차순(동률 시 id 내림차순)으로 조회합니다. 처리된 시그널을 다시 보는 흐름은 MVP 이후입니다.",
      parameters =
          @Parameter(
              name = "API-Version",
              in = ParameterIn.HEADER,
              required = false,
              example = "1"))
  @ApiResponse(
      responseCode = "200",
      description = "미처리 시그널 목록. 없으면 signals는 빈 배열입니다.",
      content = @Content(schema = @Schema(implementation = SignalListResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  SignalListResponse listSignals(
      @Parameter(description = "project 식별자") UUID projectId, Principal principal);
}
