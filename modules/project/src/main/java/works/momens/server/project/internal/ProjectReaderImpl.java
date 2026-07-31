package works.momens.server.project.internal;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.ProjectReader;
import works.momens.server.project.ProjectSnapshot;
import works.momens.server.project.TaskReader;
import works.momens.server.project.TaskStatus;

@Service
@RequiredArgsConstructor
class ProjectReaderImpl implements ProjectReader {

  /**
   * 진행률 계산에 포함되는 상태입니다. {@code cancelled}는 제외합니다.
   *
   * <p>진행률은 {@link TaskStatus} 전체에서 {@code cancelled}만 제외해 계산합니다. 계산 대상을 개별 상태로 나열하지 않는 이유는 새 상태가
   * 추가되더라도 별도 수정 없이 계산에 포함되도록 하기 위해서입니다.
   *
   * <p>전체 태스크 수와 {@code done} 태스크 수는 {@link TaskReader#countByStatus} 한 번의 결과에서 함께 계산합니다. 두 값을 따로
   * 조회하면 기준이 갈릴 수 있습니다(MOM-0779).
   */
  private static final List<String> PROGRESS_STATUSES =
      Arrays.stream(TaskStatus.values())
          .filter(status -> status != TaskStatus.CANCELLED)
          .map(TaskStatus::value)
          .toList();

  private final ProjectRepository projectRepository;
  private final TaskReader taskReader;

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> workspaceIdOf(UUID projectId) {
    return projectRepository.findByIdAndDeletedAtIsNull(projectId).map(Project::getWorkspaceId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ProjectSnapshot> findSnapshot(UUID projectId) {
    return projectRepository
        .findByIdAndDeletedAtIsNull(projectId)
        .map(ProjectReaderImpl::toSnapshot);
  }

  @Override
  @Transactional(readOnly = true)
  public OptionalInt progressOf(UUID projectId) {
    if (projectRepository.findByIdAndDeletedAtIsNull(projectId).isEmpty()) {
      return OptionalInt.empty();
    }
    Map<String, Long> countsByStatus = taskReader.countByStatus(projectId, PROGRESS_STATUSES);
    long total = countsByStatus.values().stream().mapToLong(Long::longValue).sum();
    if (total == 0) {
      return OptionalInt.of(0);
    }
    long done = countsByStatus.getOrDefault(TaskStatus.DONE.value(), 0L);
    // 정수 나눗셈을 사용하므로 소수점은 버립니다. 따라서 진행률을 올림하지 않으며, 100은 모든 태스크가 done일 때만 반환됩니다.
    return OptionalInt.of((int) (done * 100 / total));
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProjectSnapshot> listByWorkspaceIds(Collection<UUID> workspaceIds) {
    if (workspaceIds.isEmpty()) {
      return List.of();
    }
    return projectRepository
        .findByWorkspaceIdInAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceIds)
        .stream()
        .map(ProjectReaderImpl::toSnapshot)
        .toList();
  }

  private static ProjectSnapshot toSnapshot(Project project) {
    return new ProjectSnapshot(
        project.getId(),
        project.getWorkspaceId(),
        project.getName(),
        project.getTargetDate(),
        project.getSummary());
  }
}
