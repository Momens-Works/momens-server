package works.momens.server.web.workspace.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import works.momens.server.web.workspace.WorkspaceMemberView;

@Schema(description = "워크스페이스 멤버 목록 응답")
public record WorkspaceMembersResponse(
    @Schema(description = "멤버 목록(가입 시각 오름차순, 가입 시각이 같으면 사용자 식별자 오름차순). 결과가 없으면 빈 배열입니다.")
        List<WorkspaceMemberResponse> members) {

  public static WorkspaceMembersResponse from(List<WorkspaceMemberView> views) {
    return new WorkspaceMembersResponse(views.stream().map(WorkspaceMemberResponse::from).toList());
  }
}
