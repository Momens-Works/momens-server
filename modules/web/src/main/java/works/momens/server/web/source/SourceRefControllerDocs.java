package works.momens.server.web.source;

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
import works.momens.server.source.SourceErrorCode;
import works.momens.server.web.source.dto.response.WebSourceRefResponse;

/**
 * {@code /api/source-refs} endpoint의 OpenAPI 문서입니다. Swagger 애너테이션은 컨트롤러 구현과 분리합니다({@code
 * docs/spec/openapi.md}).
 *
 * <p>401 응답은 보안 필터에서 처리합니다. source-ref가 없거나 소프트 삭제된 경우에는 {@code SOURCE_REF_NOT_FOUND}(404), 요청자가
 * source-ref가 속한 워크스페이스의 멤버가 아니면 {@code AUTH_FORBIDDEN}(403)을 반환합니다.
 */
@Tag(name = "Web", description = "웹 진입 API")
interface SourceRefControllerDocs {

  @Operation(
      operationId = "verifySourceRef",
      summary = "source-ref 검증 완료 표시",
      description = "source-ref를 확인한 것으로 표시하고 검증한 사용자와 시각을 기록합니다. 해당 워크스페이스의 멤버 권한이 필요합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "source-ref 검증 완료 표시 성공",
      content = @Content(schema = @Schema(implementation = WebSourceRefResponse.class)))
  @ApiExceptions({SourceErrorCode.class, CommonErrorCode.class})
  WebSourceRefResponse verify(
      @Parameter(description = "source-ref 식별자") UUID sourceRefId, Principal principal);
}
