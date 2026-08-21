package works.momens.server.workspace.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "momens.workspace.invitation.email")
record InvitationEmailProperties(String acceptUrl, String from, String replyTo) {}
