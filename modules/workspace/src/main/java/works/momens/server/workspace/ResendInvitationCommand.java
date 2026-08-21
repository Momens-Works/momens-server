package works.momens.server.workspace;

import java.util.UUID;

public record ResendInvitationCommand(UUID workspaceId, UUID invitationId, UUID requesterUserId) {}
