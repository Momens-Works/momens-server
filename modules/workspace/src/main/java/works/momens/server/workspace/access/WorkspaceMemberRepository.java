package works.momens.server.workspace.access;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, WorkspaceMemberId> {

  Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

  List<WorkspaceMember> findByWorkspaceId(UUID workspaceId);

  List<WorkspaceMember> findByUserId(UUID userId);

  boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

  /**
   * 사용자가 아직 워크스페이스 멤버가 아닌 경우에만 멤버십을 추가하고, 실제로 추가된 행 수를 반환합니다.
   *
   * <p>먼저 조회한 뒤 멤버십이 없으면 저장하는 방식은 같은 사용자에 대한 요청이 동시에 들어올 경우 두 요청이 모두 검증을 통과할 수 있습니다. 삽입을 단일 쿼리로
   * 처리하고 복합 기본 키가 중복 삽입을 방지하도록 합니다.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO workspace_members (workspace_id, user_id, role, created_at, updated_at)
          VALUES (:workspaceId, :userId, :role, :now, :now)
          ON CONFLICT (workspace_id, user_id) DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("workspaceId") UUID workspaceId,
      @Param("userId") UUID userId,
      @Param("role") String role,
      @Param("now") Instant now);
}
