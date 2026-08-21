package works.momens.server.workspace.invitation;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import works.momens.server.user.UserService;
import works.momens.server.workspace.WorkspaceAccess;
import works.momens.server.workspace.WorkspaceInvitationAcceptor;
import works.momens.server.workspace.WorkspaceInvitationReader;
import works.momens.server.workspace.WorkspaceInvitationWriter;
import works.momens.server.workspace.WorkspaceMembershipWriter;
import works.momens.server.workspace.WorkspaceReader;
import works.momens.server.workspace.email.InvitationEmailSender;

@Configuration
class InvitationConfig {

  @Bean
  WorkspaceInvitationReader workspaceInvitationReader(WorkspaceInvitationRepository repository) {
    return new WorkspaceInvitationReaderImpl(repository, Clock.systemUTC());
  }

  @Bean
  WorkspaceInvitationWriter workspaceInvitationWriter(
      WorkspaceInvitationRepository repository,
      JdbcClient jdbcClient,
      TransactionTemplate transactionTemplate,
      WorkspaceReader workspaceReader,
      WorkspaceAccess workspaceAccess,
      UserService userService,
      InvitationEmailSender emailSender) {
    return new WorkspaceInvitationWriterImpl(
        repository,
        new PendingInvitationUpserter(jdbcClient),
        transactionTemplate,
        workspaceReader,
        workspaceAccess,
        userService,
        emailSender,
        Clock.systemUTC());
  }

  @Bean
  WorkspaceInvitationAcceptor workspaceInvitationAcceptor(
      WorkspaceInvitationRepository repository,
      WorkspaceMembershipWriter membershipWriter,
      WorkspaceReader workspaceReader,
      UserService userService) {
    return new WorkspaceInvitationAcceptorImpl(
        repository, membershipWriter, workspaceReader, userService, Clock.systemUTC());
  }
}
