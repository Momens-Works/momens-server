package works.momens.server.web.workspace.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code PATCH /api/workspaces/{workspaceId}} 요청.
 *
 * <p>세 필드는 모두 선택 사항입니다. 필드를 보내지 않거나 빈 문자열로 보내면 기존 값을 유지합니다. 따라서 이 요청으로 description을 빈 값으로 변경할 수는
 * 없습니다.
 */
@Schema(description = "워크스페이스 수정 요청")
public record UpdateWorkspaceRequest(
    @Schema(description = "워크스페이스 이름. 값이 없으면 기존 이름을 유지합니다.") String name,
    @Schema(description = "워크스페이스 설명. 값이 없으면 기존 설명을 유지합니다.") String description,
    @Schema(description = "워크스페이스 slug. 값이 없으면 기존 slug를 유지합니다.") String slug) {}
