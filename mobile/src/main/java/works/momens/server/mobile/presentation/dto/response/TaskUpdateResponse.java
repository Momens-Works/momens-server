package works.momens.server.mobile.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import works.momens.server.mobile.internal.MobileTaskDetail;

/**
 * {@code PATCH /api/mobile/tasks/{taskId}} 응답. 응답 형식은 docs/spec/mobile-api.md 태스크 수정 절을 따릅니다.
 *
 * <p>수정을 저장하면 앱이 상세 화면으로 돌아가므로, 상세 조회와 같은 형식의 태스크 전체를 {@code task}로 감싸 반환합니다. 클라이언트가 다시 조회하지 않아도
 * 화면을 갱신할 수 있습니다. 상세와 같은 형식이라 status와 완료기준까지 그대로 담습니다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "태스크 수정 응답")
public record TaskUpdateResponse(@Schema(description = "수정된 태스크") TaskDetailResponse task) {

  public static TaskUpdateResponse from(MobileTaskDetail detail) {
    return new TaskUpdateResponse(TaskDetailResponse.from(detail));
  }
}
