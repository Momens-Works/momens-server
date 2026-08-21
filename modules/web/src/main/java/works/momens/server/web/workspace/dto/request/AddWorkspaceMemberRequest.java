package works.momens.server.web.workspace.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "초대 없이 워크스페이스 멤버를 바로 추가하는 요청")
public record AddWorkspaceMemberRequest(
    @Schema(description = "추가할 사용자의 이메일", example = "jinsu@momens.works") String email,
    @Schema(description = "추가할 멤버에게 부여할 역할입니다. admin과 member만 허용합니다.", example = "member")
        String role) {}
