package works.momens.server.signal.dev;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import works.momens.server.common.api.ApiExceptions;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.signal.dev.dto.request.CreateDevSignalRequest;
import works.momens.server.signal.dev.dto.response.CreateDevSignalResponse;

/**
 * {@code /api/dev/projects/{projectId}/signals} OpenAPI 문서. Swagger 애너테이션을 컨트롤러 구현과
 * 분리합니다(docs/spec/openapi.md).
 *
 * <p>401은 보안 필터가 Standard shape로 응답하고, 없는 project는 PROJECT_NOT_FOUND(404)입니다. 데모 도구라 멤버십 부족으로 인한
 * 403은 반환하지 않습니다.
 */
@Tag(name = "DevSignal", description = "dev 데모용 Signal 생성 API. local/dev/test 프로필에서만 존재합니다.")
interface DevSignalControllerDocs {

  @Operation(
      summary = "dev 데모용 Signal 생성",
      description =
          "완전한 Signal(evidence 원본 포함)을 생성하고 signal.created outbox 이벤트를 같은 트랜잭션으로 저장합니다. commit되면"
              + " api-server의 notification consumer가 workspace 전체 구성원의 활성 Android 기기로 FCM push를"
              + " 발송합니다. 응답은 commit 완료를 뜻하며 FCM 발송 성공을 기다리지 않습니다.")
  @ApiResponse(
      responseCode = "201",
      description = "생성됨. 생성된 Signal 식별자를 반환합니다.",
      content = @Content(schema = @Schema(implementation = CreateDevSignalResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  CreateDevSignalResponse createSignal(
      @Parameter(description = "project 식별자") UUID projectId, CreateDevSignalRequest request);
}
