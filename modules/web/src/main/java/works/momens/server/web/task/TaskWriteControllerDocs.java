package works.momens.server.web.task;

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
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.web.dto.response.WebMessageResponse;
import works.momens.server.web.task.dto.request.CreateTaskUpdateRequest;
import works.momens.server.web.task.dto.request.CreateWebTaskRequest;
import works.momens.server.web.task.dto.request.UpdateWebTaskRequest;
import works.momens.server.web.task.dto.response.TaskUpdateListResponse.UpdateResponse;
import works.momens.server.web.task.dto.response.WebTaskResponse;

/** {@code /api} 웹 태스크 쓰기 OpenAPI 문서. */
@Tag(name = "Web", description = "웹 진입 API")
interface TaskWriteControllerDocs {

  @Operation(operationId = "createProjectTask", summary = "프로젝트 태스크 생성")
  @ApiResponse(
      responseCode = "201",
      content = @Content(schema = @Schema(implementation = WebTaskResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  WebTaskResponse create(
      @Parameter(description = "프로젝트 식별자") UUID projectId,
      CreateWebTaskRequest request,
      Principal principal);

  @Operation(operationId = "updateTask", summary = "태스크 수정")
  @ApiResponse(
      responseCode = "200",
      content = @Content(schema = @Schema(implementation = WebTaskResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  WebTaskResponse update(
      @Parameter(description = "태스크 식별자") UUID taskId,
      UpdateWebTaskRequest request,
      Principal principal);

  @Operation(operationId = "deleteTask", summary = "태스크 삭제")
  @ApiResponse(
      responseCode = "200",
      content = @Content(schema = @Schema(implementation = WebMessageResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  WebMessageResponse delete(@Parameter(description = "태스크 식별자") UUID taskId, Principal principal);

  @Operation(operationId = "createTaskUpdate", summary = "태스크 업데이트 생성")
  @ApiResponse(
      responseCode = "201",
      content = @Content(schema = @Schema(implementation = UpdateResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  UpdateResponse createUpdate(
      @Parameter(description = "태스크 식별자") UUID taskId,
      CreateTaskUpdateRequest request,
      Principal principal);

  @Operation(operationId = "deleteTaskUpdate", summary = "태스크 업데이트 삭제")
  @ApiResponse(
      responseCode = "200",
      content = @Content(schema = @Schema(implementation = WebMessageResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, CommonErrorCode.class})
  WebMessageResponse deleteUpdate(
      @Parameter(description = "태스크 식별자") UUID taskId,
      @Parameter(description = "태스크 업데이트 식별자") UUID updateId,
      Principal principal);
}
