package works.momens.server.workspace.access;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, WorkspaceMemberId> {

  Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

  List<WorkspaceMember> findByWorkspaceId(UUID workspaceId);

  List<WorkspaceMember> findByUserId(UUID userId);

  boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
