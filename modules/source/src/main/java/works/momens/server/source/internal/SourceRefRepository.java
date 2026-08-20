package works.momens.server.source.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SourceRefRepository extends JpaRepository<SourceRef, UUID> {

  @Query("select r.workspaceId from SourceRef r where r.id = :sourceRefId and r.deletedAt is null")
  Optional<UUID> findWorkspaceId(@Param("sourceRefId") UUID sourceRefId);

  List<SourceRef> findByWorkspaceIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
      UUID workspaceId, Collection<UUID> ids);
}
