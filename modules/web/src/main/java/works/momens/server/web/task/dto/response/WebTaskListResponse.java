package works.momens.server.web.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import works.momens.server.project.WebTaskDetail;

@Schema(description = "프로젝트 태스크 목록 응답")
public record WebTaskListResponse(
    @Schema(description = "생성 시각 내림차순 태스크 목록") List<WebTaskResponse> tasks) {
  public static WebTaskListResponse from(List<WebTaskDetail> tasks) {
    return new WebTaskListResponse(tasks.stream().map(WebTaskResponse::from).toList());
  }
}
