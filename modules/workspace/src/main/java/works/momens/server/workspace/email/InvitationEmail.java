package works.momens.server.workspace.email;

import java.time.Instant;

public record InvitationEmail(
    String workspaceName,
    String inviterName,
    String inviterEmail,
    String recipientEmail,
    String role,
    String rawToken,
    Instant expiresAt) {}
