package works.momens.server.workspace;

import java.util.UUID;

public record AddMembershipByEmailCommand(UUID workspaceId, String email, WorkspaceRole role) {}
