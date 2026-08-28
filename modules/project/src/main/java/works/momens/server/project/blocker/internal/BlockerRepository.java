package works.momens.server.project.blocker.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import works.momens.server.project.blocker.BlockerDetail;

interface BlockerRepository extends JpaRepository<Blocker, UUID> {

  @Query(
      """
      select new works.momens.server.project.blocker.BlockerDetail(
          b.id, b.workspaceId, b.description, b.status, b.blockedEntityType,
          b.blockedEntityId, b.createdAt, b.updatedAt, b.resolvedAt)
      from Blocker b
      where b.workspaceId = :workspaceId
      order by b.createdAt desc
      """)
  List<BlockerDetail> findDetailsByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
