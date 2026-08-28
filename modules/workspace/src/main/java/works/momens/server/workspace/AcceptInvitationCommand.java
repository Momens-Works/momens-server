package works.momens.server.workspace;

import java.util.UUID;

public record AcceptInvitationCommand(UUID userId, String rawToken) {}
