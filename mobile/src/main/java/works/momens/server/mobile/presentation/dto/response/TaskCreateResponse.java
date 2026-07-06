package works.momens.server.mobile.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import works.momens.server.project.CreatedTask;

/** {@code POST /api/mobile/projects/{projectId}/tasks} 응답. 생성된 태스크를 {@code task} 아래에 담습니다. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "태스크 생성 응답")
public record TaskCreateResponse(@Schema(description = "생성된 태스크") TaskResponse task) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @Schema(description = "생성된 태스크")
  public record TaskResponse(
      UUID id, UUID projectId, String title, List<String> roles, String priority, String status) {}

  public static TaskCreateResponse from(CreatedTask created) {
    return new TaskCreateResponse(
        new TaskResponse(
            created.id(),
            created.projectId(),
            created.title(),
            created.roles(),
            created.priority(),
            created.status()));
  }
}
