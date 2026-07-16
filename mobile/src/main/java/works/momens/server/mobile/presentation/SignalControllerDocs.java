package works.momens.server.mobile.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import works.momens.server.common.api.ApiExceptions;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.mobile.presentation.dto.response.ConvertToTaskResponse;
import works.momens.server.mobile.presentation.dto.response.DismissResponse;
import works.momens.server.mobile.presentation.dto.response.SignalDetailResponse;
import works.momens.server.mobile.presentation.dto.response.SignalListResponse;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.signal.SignalErrorCode;

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
          "프로젝트의 아직 처리되지 않은 시그널을 생성 시각 내림차순(동률 시 id 내림차순)으로 조회합니다. 처리된 시그널을 다시 보는 흐름은 MVP 이후입니다.")
  @ApiResponse(
      responseCode = "200",
      description = "미처리 시그널 목록. 없으면 signals는 빈 배열입니다.",
      content = @Content(schema = @Schema(implementation = SignalListResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  SignalListResponse listSignals(
      @Parameter(description = "project 식별자") UUID projectId, Principal principal);

  @Operation(
      summary = "시그널 상세 조회",
      description =
          "시그널 상세 bottom sheet에 필요한 근거(대상·변화·영향)와 민수 제안을 조회합니다. 미처리 시그널만 대상이며, 처리·삭제된 시그널은"
              + " SIGNAL_NOT_FOUND입니다.")
  @ApiResponse(
      responseCode = "200",
      description = "시그널 상세.",
      content = @Content(schema = @Schema(implementation = SignalDetailResponse.class)))
  @ApiExceptions({SignalErrorCode.class, CommonErrorCode.class})
  SignalDetailResponse getSignal(
      @Parameter(description = "signal 식별자") UUID signalId, Principal principal);

  @Operation(
      summary = "시그널을 태스크로 전환",
      description =
          "시그널을 원탭으로 태스크에 등록합니다. 요청 body는 없습니다(ADR-0011). 서버가 민수 task draft(title은 시그널 제목, role은 pm,"
              + " priority는 medium)로 태스크를 만듭니다. 같은 시그널에 같은 action을 재요청하면 새 태스크를 만들지 않고 기존 결과를 200으로"
              + " 반환합니다.")
  @ApiResponse(
      responseCode = "201",
      description = "새로 전환됨.",
      content = @Content(schema = @Schema(implementation = ConvertToTaskResponse.class)))
  @ApiResponse(
      responseCode = "200",
      description = "같은 action 재요청(멱등 replay).",
      content = @Content(schema = @Schema(implementation = ConvertToTaskResponse.class)))
  @ApiExceptions({SignalErrorCode.class, CommonErrorCode.class})
  ResponseEntity<ConvertToTaskResponse> convertToTask(
      @Parameter(description = "signal 식별자") UUID signalId, Principal principal);

  @Operation(
      summary = "시그널을 처리하지 않고 닫음(dismiss)",
      description =
          "시그널을 태스크로 전환하지 않고 처리 완료로 기록합니다. 물리 삭제가 아니며, 같은 시그널에 dismiss를 재요청하면 기존 결과를 200으로"
              + " 반환합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "처리 완료(신규 또는 멱등 replay).",
      content = @Content(schema = @Schema(implementation = DismissResponse.class)))
  @ApiExceptions({SignalErrorCode.class, CommonErrorCode.class})
  DismissResponse dismiss(
      @Parameter(description = "signal 식별자") UUID signalId, Principal principal);
}
