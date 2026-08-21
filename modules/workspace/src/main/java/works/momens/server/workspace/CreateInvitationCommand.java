package works.momens.server.workspace;

import java.util.UUID;

public record CreateInvitationCommand(
    UUID workspaceId, UUID inviterUserId, String email, WorkspaceRole role) {}
