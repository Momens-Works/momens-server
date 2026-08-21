package works.momens.server.web.workspace.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "초대 수락 응답")
public record AcceptInvitationResponse(
    @Schema(description = "초대를 수락해 참여한 워크스페이스") WorkspaceResponse workspace,
    @Schema(description = "새로 생성된 멤버십") WorkspaceMemberResponse member) {}
