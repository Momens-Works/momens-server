package works.momens.server.mobile.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.BoardTask;
import works.momens.server.project.CreateTaskCommand;
import works.momens.server.project.CreatedTask;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.project.ProjectReader;
import works.momens.server.project.TaskCreator;
import works.momens.server.project.TaskReader;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * 프로젝트 태스크 보드 조회와 생성 조합 서비스. project(태스크 도메인)와 workspace(멤버십) public API를 조합하고 도메인 정책을 소유하지 않습니다.
 *
 * <p>보드 그룹 구성과 모바일 priority 매핑(urgent를 high로 반환), material_count 기본값은 모바일 조합 규칙이라 이 서비스가 소유합니다. 보드
 * 조회는 read-only 트랜잭션에 두고, 생성은 라벨 발급과 저장이 한 트랜잭션으로 묶이도록 쓰기 트랜잭션에 둡니다.
 */
@Service
@RequiredArgsConstructor
public class ProjectTaskService {

  private final ProjectReader projectReader;
  private final WorkspaceAccess workspaceAccess;
  private final TaskReader taskReader;
  private final TaskCreator taskCreator;

  @Transactional(readOnly = true)
  public List<MobileTaskGroup> getBoard(UUID projectId, UUID userId) {
    requireProjectMember(projectId, userId);
    Map<String, List<MobileTaskCard>> cardsByStatus =
        taskReader.listTasksByStatus(projectId, BoardStatus.keys()).stream()
            .collect(
                Collectors.groupingBy(
                    BoardTask::status,
                    Collectors.mapping(ProjectTaskService::toCard, Collectors.toList())));
    return Arrays.stream(BoardStatus.values())
        .map(
            status ->
                new MobileTaskGroup(status, cardsByStatus.getOrDefault(status.key(), List.of())))
        .toList();
  }

  @Transactional
  public CreatedTask createTask(
      UUID projectId, UUID userId, String title, List<String> roles, String priority) {
    UUID workspaceId = requireProjectMember(projectId, userId);
    return taskCreator.create(
        new CreateTaskCommand(projectId, workspaceId, title, roles, priority));
  }

  private UUID requireProjectMember(UUID projectId, UUID userId) {
    UUID workspaceId =
        projectReader
            .workspaceIdOf(projectId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ProjectErrorCode.PROJECT_NOT_FOUND,
                        Map.of("project_id", projectId.toString())));
    if (!workspaceAccess.isMember(workspaceId, userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("project_id", projectId.toString()));
    }
    return workspaceId;
  }

  private static MobileTaskCard toCard(BoardTask task) {
    // 관련 자료 연결(entity_relations)이 서버에 아직 없어 material_count는 0으로 둔다(명세 합성 필드 정책).
    return new MobileTaskCard(
        task.id(), task.title(), task.roles(), mapPriority(task.priority()), 0);
  }

  private static String mapPriority(String storedPriority) {
    // 모바일 enum은 low/medium/high 3종이라, 레거시에만 있는 urgent는 high로 반환한다(가결정).
    return "urgent".equals(storedPriority) ? "high" : storedPriority;
  }
}
