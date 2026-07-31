package works.momens.server.project.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectRepository extends JpaRepository<Project, UUID> {

  Optional<Project> findByIdAndDeletedAtIsNull(UUID id);

  List<Project> findByWorkspaceIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
      Collection<UUID> workspaceIds);
}
