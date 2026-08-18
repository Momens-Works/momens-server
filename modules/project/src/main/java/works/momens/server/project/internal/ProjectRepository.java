package works.momens.server.project.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProjectRepository extends JpaRepository<Project, UUID> {

  Optional<Project> findByIdAndDeletedAtIsNull(UUID id);

  List<Project> findByWorkspaceIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
      Collection<UUID> workspaceIds);

  /**
   * 웹 상세 조회용 DTO projection입니다. 엔티티를 영속성 컨텍스트에 올리지 않고 필요한 컬럼만 읽습니다.
   *
   * <p>소유자는 여기서 조인하지 않습니다. 레거시는 행마다 상관 서브쿼리로 집계하지만, 계약이 배치 조회를 허용하고 행별 추가 조회를 금지합니다(웹 snapshot 계약
   * 4.7).
   */
  @Query(
      """
      select new works.momens.server.project.internal.ProjectDetailRow(
          p.id, p.workspaceId, p.label, p.name, p.description, p.status, p.ownerId,
          p.targetDate, p.healthStatus, p.summary, p.unresolvedCount, p.vocSignalCount,
          p.lastContextAt, p.metadata, p.createdAt, p.updatedAt)
      from Project p
      where p.id = :projectId and p.deletedAt is null
      """)
  Optional<ProjectDetailRow> findDetailRow(@Param("projectId") UUID projectId);

  /** 위와 같은 projection의 workspace 범위 목록입니다. 정렬은 레거시와 같은 생성 시각 내림차순입니다. */
  @Query(
      """
      select new works.momens.server.project.internal.ProjectDetailRow(
          p.id, p.workspaceId, p.label, p.name, p.description, p.status, p.ownerId,
          p.targetDate, p.healthStatus, p.summary, p.unresolvedCount, p.vocSignalCount,
          p.lastContextAt, p.metadata, p.createdAt, p.updatedAt)
      from Project p
      where p.workspaceId = :workspaceId and p.deletedAt is null
      order by p.createdAt desc
      """)
  List<ProjectDetailRow> findDetailRowsByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
