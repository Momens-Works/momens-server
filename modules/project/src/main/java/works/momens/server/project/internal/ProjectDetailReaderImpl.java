package works.momens.server.project.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.ProjectDetail;
import works.momens.server.project.ProjectDetailReader;

@Service
@RequiredArgsConstructor
class ProjectDetailReaderImpl implements ProjectDetailReader {

  private final ProjectRepository projectRepository;
  private final ProjectOwnerRepository projectOwnerRepository;

  @Override
  @Transactional(readOnly = true)
  public List<ProjectDetail> listDetailsByWorkspaceId(UUID workspaceId) {
    return withOwners(projectRepository.findDetailRowsByWorkspaceId(workspaceId));
  }

  /**
   * 행 목록에 소유자를 채웁니다. {@code project_owners} 조회는 project 수와 무관하게 한 번입니다.
   *
   * <p>소유자 행이 없으면 {@code owner_id} 하나만 담습니다. 레거시가 집계 결과에 {@code COALESCE(..., ARRAY[p.owner_id])}를
   * 씌우는 동작과 같고, 그래서 {@code owner_user_ids}는 절대 비지 않습니다.
   */
  private List<ProjectDetail> withOwners(List<ProjectDetailRow> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    List<UUID> projectIds = rows.stream().map(ProjectDetailRow::id).toList();
    Map<UUID, List<UUID>> ownersByProjectId =
        projectOwnerRepository
            .findByProjectIdInOrderByCreatedAtAscOwnerUserIdAsc(projectIds)
            .stream()
            .collect(
                Collectors.groupingBy(
                    ProjectOwner::getProjectId,
                    // groupingBy는 스트림 순서를 유지하므로 정렬은 조회에서 정해진 그대로다.
                    Collectors.mapping(ProjectOwner::getOwnerUserId, Collectors.toList())));
    return rows.stream()
        .map(row -> row.toDetail(ownersByProjectId.getOrDefault(row.id(), List.of(row.ownerId()))))
        .toList();
  }
}
