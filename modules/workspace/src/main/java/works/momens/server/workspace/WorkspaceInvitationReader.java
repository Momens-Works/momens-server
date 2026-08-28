package works.momens.server.workspace;

import java.util.List;
import java.util.UUID;

public interface WorkspaceInvitationReader {

  List<WorkspaceInvitationDetail> listByWorkspaceId(UUID workspaceId);
}
