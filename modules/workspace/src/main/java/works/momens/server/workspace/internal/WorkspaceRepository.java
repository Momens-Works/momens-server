package works.momens.server.workspace.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

  Optional<Workspace> findBySlug(String slug);

  boolean existsBySlug(String slug);

  /**
   * userId가 멤버인 워크스페이스를 {@code workspace_members} 조인으로 조회합니다. 정렬은 레거시와 같은 생성 시각 내림차순으로 쿼리에서
   * 고정합니다(docs/design/legacy-product-api-migration/slice-workspace-read.md 4.3).
   *
   * <p>{@link Workspace}와 {@code WorkspaceMember}(access 하위 도메인) 사이에 JPA 연관관계를 두지 않으므로 native
   * query로 테이블을 직접 조인합니다.
   */
  @Query(
      value =
          "SELECT w.* FROM workspaces w "
              + "JOIN workspace_members wm ON wm.workspace_id = w.id "
              + "WHERE wm.user_id = :userId "
              + "ORDER BY w.created_at DESC",
      nativeQuery = true)
  List<Workspace> findByMemberUserId(@Param("userId") UUID userId);
}
