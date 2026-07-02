package works.momens.server.workspace.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, WorkspaceMemberId> {

  List<WorkspaceMember> findByWorkspaceId(UUID workspaceId);

  boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
