package works.momens.server.web.source;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.UUID;
import works.momens.server.common.api.ApiException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.source.SourceErrorCode;
import works.momens.server.web.dto.response.WebMessageResponse;
import works.momens.server.workspace.WorkspaceErrorCode;

@Tag(name = "Web", description = "웹 진입 API")
interface SourceConnectionWriteControllerDocs {

  @Operation(
      operationId = "disableSourceConnection",
      summary = "source 연결 비활성화",
      description =
          "admin 또는 owner가 source 연결을 비활성화합니다. 이미 비활성화된 연결은 400을 반환합니다."
              + " Figma webhook 삭제 실패는 비활성화를 막지 않습니다. 오류는 Standard 형식입니다.")
  @ApiResponse(
      responseCode = "200",
      description = "연결 비활성화 성공",
      content =
          @Content(
              schema = @Schema(implementation = WebMessageResponse.class),
              examples = @ExampleObject(value = "{\"message\":\"disabled\"}")))
  @ApiException(
      value = SourceErrorCode.class,
      codes = {"SOURCE_CONNECTION_NOT_FOUND", "SOURCE_CONNECTION_ALREADY_DISABLED"})
  @ApiException(
      value = WorkspaceErrorCode.class,
      codes = {"WORKSPACE_NOT_FOUND"})
  @ApiException(CommonErrorCode.class)
  WebMessageResponse disable(
      @Parameter(description = "source 연결 식별자") UUID connectionId, Principal principal);
}
