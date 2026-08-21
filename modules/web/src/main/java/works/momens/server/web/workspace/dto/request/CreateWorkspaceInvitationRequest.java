package works.momens.server.web.workspace.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "워크스페이스 초대 생성 요청")
public record CreateWorkspaceInvitationRequest(
    @Schema(description = "초대할 사용자의 이메일", example = "jinsu@momens.works") String email,
    @Schema(description = "초대 수락 시 부여할 역할입니다. admin과 member만 허용합니다.", example = "member")
        String role) {}
