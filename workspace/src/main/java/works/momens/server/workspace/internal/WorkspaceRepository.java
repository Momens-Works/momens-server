package works.momens.server.workspace.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

  Optional<Workspace> findBySlug(String slug);
}
