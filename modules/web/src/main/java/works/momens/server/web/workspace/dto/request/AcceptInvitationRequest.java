package works.momens.server.web.workspace.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "초대 수락 요청")
public record AcceptInvitationRequest(@Schema(description = "초대 링크에 포함된 토큰") String token) {}
