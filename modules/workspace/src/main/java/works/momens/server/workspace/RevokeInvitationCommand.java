package works.momens.server.workspace;

import java.util.UUID;

public record RevokeInvitationCommand(UUID workspaceId, UUID invitationId) {}
