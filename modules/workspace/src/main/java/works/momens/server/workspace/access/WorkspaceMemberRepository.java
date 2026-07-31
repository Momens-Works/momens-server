package works.momens.server.workspace.access;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, WorkspaceMemberId> {

  List<WorkspaceMember> findByWorkspaceId(UUID workspaceId);

  List<WorkspaceMember> findByUserId(UUID userId);

  boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
