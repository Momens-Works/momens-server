package works.momens.server.web.task;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.UUID;
import works.momens.server.common.api.ApiException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.core.ProjectErrorCode;
import works.momens.server.project.task.TaskErrorCode;
import works.momens.server.web.task.dto.response.TaskContextResponse;
import works.momens.server.web.task.dto.response.TaskUpdateListResponse;
import works.momens.server.web.task.dto.response.WebTaskListResponse;
import works.momens.server.web.task.dto.response.WebTaskResponse;

/** {@code /api} 웹 태스크 조회 OpenAPI 문서. */
@Tag(name = "Web", description = "웹 진입 API")
interface TaskReadControllerDocs {

  @Operation(
      operationId = "listProjectTasks",
      summary = "프로젝트 태스크 목록 조회",
      description = "프로젝트의 소프트 삭제되지 않은 태스크를 생성 시각 내림차순으로 조회합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공. 태스크가 없으면 tasks는 빈 배열입니다.",
      content = @Content(schema = @Schema(implementation = WebTaskListResponse.class)))
  @ApiException(ProjectErrorCode.class)
  @ApiException(TaskErrorCode.class)
  @ApiException(CommonErrorCode.class)
  WebTaskListResponse list(
      @Parameter(description = "프로젝트 식별자") UUID projectId, Principal principal);

  @Operation(
      operationId = "getTask",
      summary = "태스크 상세 조회",
      description = "소프트 삭제되지 않은 태스크와 소속 프로젝트의 상세를 조회합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공",
      content = @Content(schema = @Schema(implementation = WebTaskResponse.class)))
  @ApiException(ProjectErrorCode.class)
  @ApiException(TaskErrorCode.class)
  @ApiException(CommonErrorCode.class)
  WebTaskResponse get(@Parameter(description = "태스크 식별자") UUID taskId, Principal principal);

  @Operation(
      operationId = "listTaskUpdates",
      summary = "태스크 업데이트 목록 조회",
      description = "태스크의 소프트 삭제되지 않은 업데이트를 생성 시각 오름차순으로 조회합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공. 업데이트가 없으면 updates는 빈 배열입니다.",
      content = @Content(schema = @Schema(implementation = TaskUpdateListResponse.class)))
  @ApiException(ProjectErrorCode.class)
  @ApiException(TaskErrorCode.class)
  @ApiException(CommonErrorCode.class)
  TaskUpdateListResponse updates(
      @Parameter(description = "태스크 식별자") UUID taskId, Principal principal);

  @Operation(
      operationId = "getTaskContext",
      summary = "태스크 컨텍스트 조회",
      description = "태스크에 연결된 소프트 삭제되지 않은 memory와 source ref를 생성 시각 내림차순으로 조회합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공. 연결된 항목이 없으면 memories와 source_refs는 빈 배열입니다.",
      content = @Content(schema = @Schema(implementation = TaskContextResponse.class)))
  @ApiException(ProjectErrorCode.class)
  @ApiException(TaskErrorCode.class)
  @ApiException(CommonErrorCode.class)
  TaskContextResponse context(@Parameter(description = "태스크 식별자") UUID taskId, Principal principal);
}
