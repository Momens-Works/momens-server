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
import works.momens.server.mobile.presentation.dto.response.BriefSignalSummaryPageResponse;
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

  @Operation(
      summary = "프로젝트 브리프 조회",
      description =
          "브리프 화면(오늘의 브리프)의 초기 로드에 필요한 정보를 조회합니다. 시그널 요약은 당일 시그널의 최신순 첫 페이지(기본 3개)와 타입별"
              + " 개수를 담고, change(VOC)도 다른 type과 똑같이 포함합니다. 필터 전환과 더보기는 하위 엔드포인트"
              + " GET .../brief/signal-summary를 사용합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "브리프 조회 성공",
      content = @Content(schema = @Schema(implementation = BriefResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  BriefResponse getBrief(
      @Parameter(description = "project 식별자") UUID projectId, Principal principal);

  @Operation(
      summary = "브리프 시그널 요약 페이지 조회",
      description = "브리프 시그널 요약의 필터 전환과 더보기 페이지 이동에 사용합니다. 커서 기반 페이지네이션이고 정렬은 최신순입니다.")
  @ApiResponse(
      responseCode = "200",
      description = "페이지 조회 성공",
      content = @Content(schema = @Schema(implementation = BriefSignalSummaryPageResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  BriefSignalSummaryPageResponse getSignalSummaryPage(
      @Parameter(description = "project 식별자") UUID projectId,
      @Parameter(
              description = "필터 키. all 또는 signal type(change, decision, risk, question). 기본값은 all")
          String filter,
      @Parameter(description = "이전 응답의 next_cursor. 없으면 첫 페이지") String cursor,
      @Parameter(description = "페이지 크기. 없거나 0이면 기본값 3, 상한 50") Integer limit,
      Principal principal);
}
