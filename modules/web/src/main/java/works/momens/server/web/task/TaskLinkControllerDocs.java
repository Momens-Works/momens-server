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
import works.momens.server.context.ContextErrorCode;
import works.momens.server.memory.MemoryErrorCode;
import works.momens.server.project.core.ProjectErrorCode;
import works.momens.server.project.task.TaskErrorCode;
import works.momens.server.web.dto.response.WebMessageResponse;
import works.momens.server.web.task.dto.request.CreateTaskSourceRefRequest;
import works.momens.server.web.task.dto.response.CreateTaskSourceRefResponse;

/** {@code /api} 웹 태스크 연결 endpoint의 OpenAPI 문서입니다. */
@Tag(name = "Web", description = "웹 진입 API")
interface TaskLinkControllerDocs {

  @Operation(
      operationId = "linkTaskMemory",
      summary = "태스크에 메모리 연결",
      description = "워크스페이스 멤버가 태스크에 확정 메모리를 연결합니다. 이미 연결되어 있으면 변경하지 않고 같은 응답을 반환합니다.")
  @ApiResponse(
      responseCode = "201",
      description = "연결 성공",
      content = @Content(schema = @Schema(implementation = WebMessageResponse.class)))
  @ApiExceptions({
    ProjectErrorCode.class,
    TaskErrorCode.class,
    MemoryErrorCode.class,
    ContextErrorCode.class,
    CommonErrorCode.class
  })
  WebMessageResponse linkTaskMemory(
      @Parameter(description = "태스크 식별자") UUID taskId,
      @Parameter(description = "메모리 식별자") UUID memoryId,
      Principal principal);

  @Operation(
      operationId = "unlinkTaskMemory",
      summary = "태스크의 메모리 연결 해제",
      description = "워크스페이스 멤버가 태스크와 메모리의 연결을 해제합니다. 해제할 연결이 없으면 404로 응답합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "해제 성공",
      content = @Content(schema = @Schema(implementation = WebMessageResponse.class)))
  @ApiExceptions({
    ProjectErrorCode.class,
    TaskErrorCode.class,
    ContextErrorCode.class,
    CommonErrorCode.class
  })
  WebMessageResponse unlinkTaskMemory(
      @Parameter(description = "태스크 식별자") UUID taskId,
      @Parameter(description = "메모리 식별자") UUID memoryId,
      Principal principal);

  @Operation(
      operationId = "createTaskSourceRef",
      summary = "태스크에 링크 첨부",
      description =
          "워크스페이스 멤버가 입력한 주소로 source-ref를 생성하고 태스크에 연결합니다. 같은 주소를 두 번 첨부하면 source-ref가 두 건 생성됩니다.")
  @ApiResponse(
      responseCode = "201",
      description = "생성 성공",
      content = @Content(schema = @Schema(implementation = CreateTaskSourceRefResponse.class)))
  @ApiExceptions({ProjectErrorCode.class, TaskErrorCode.class, CommonErrorCode.class})
  CreateTaskSourceRefResponse createTaskSourceRef(
      @Parameter(description = "태스크 식별자") UUID taskId,
      CreateTaskSourceRefRequest request,
      Principal principal);

  @Operation(
      operationId = "unlinkTaskSourceRef",
      summary = "태스크의 source-ref 연결 해제",
      description = "워크스페이스 멤버가 태스크와 source-ref의 연결을 해제합니다. source-ref 행은 삭제하지 않습니다.")
  @ApiResponse(
      responseCode = "200",
      description = "해제 성공",
      content = @Content(schema = @Schema(implementation = WebMessageResponse.class)))
  @ApiExceptions({
    ProjectErrorCode.class,
    TaskErrorCode.class,
    ContextErrorCode.class,
    CommonErrorCode.class
  })
  WebMessageResponse unlinkTaskSourceRef(
      @Parameter(description = "태스크 식별자") UUID taskId,
      @Parameter(description = "source-ref 식별자") UUID sourceRefId,
      Principal principal);
}
