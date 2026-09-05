package works.momens.server.mobile.bootstrap;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import works.momens.server.common.api.ApiException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.mobile.bootstrap.dto.response.BootstrapResponse;
import works.momens.server.user.UserErrorCode;

/**
 * {@code /api/mobile/bootstrap} OpenAPI 문서. Swagger 애너테이션을 컨트롤러 구현과 분리합니다(docs/spec/openapi.md).
 *
 * <p>401/403은 보안 필터가 Standard shape로 응답하고, 유효한 토큰인데 사용자 행이 없는 경우는 {@code /api/me}와 같은
 * USER_NOT_FOUND(404)입니다.
 */
@Tag(name = "Mobile", description = "모바일 앱 진입 API")
interface BootstrapControllerDocs {

  @Operation(
      operationId = "mobileGetBootstrap",
      summary = "모바일 부트스트랩 조회",
      description = "로그인 후 앱 진입에 필요한 현재 사용자 정보, 기본 project id, 접근 가능한 project 목록을 조회합니다.")
  @ApiResponse(
      responseCode = "200",
      description =
          "모바일 진입 컨텍스트 조회 성공. 접근 가능한 project가 없으면 default_project_id는 null, projects는 빈 배열입니다.",
      content = @Content(schema = @Schema(implementation = BootstrapResponse.class)))
  @ApiException(
      value = UserErrorCode.class,
      codes = {"USER_NOT_FOUND"})
  @ApiException(CommonErrorCode.class)
  BootstrapResponse getBootstrap(Principal principal);
}
