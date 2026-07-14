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
import works.momens.server.project.TaskDetail;
import works.momens.server.project.TaskEditor;
import works.momens.server.project.TaskReader;
import works.momens.server.project.UpdateTaskCommand;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * 모바일 태스크 표면(보드 조회, 생성, 상세 조회)의 조합 서비스. project(태스크 도메인), workspace(멤버십), user(프로필) public API를
 * 조합하고 도메인 정책을 소유하지 않습니다.
 *
 * <p>보드 그룹 구성과 material_count 기본값, 상세의 purpose 개명은 모바일 조합 규칙이므로 이 서비스가 소유하고, 저장 priority 해석(urgent를
 * high로 반환)은 {@link MobilePriority}가 소유합니다. 조회는 read-only 트랜잭션에 두고, 생성은 라벨 발급과 저장이 한 트랜잭션으로 묶이도록 쓰기
 * 트랜잭션에 둡니다.
 */
@Service
@RequiredArgsConstructor
public class ProjectTaskService {

  private final ProjectReader projectReader;
  private final WorkspaceAccess workspaceAccess;
  private final TaskReader taskReader;
  private final TaskCreator taskCreator;
  private final TaskEditor taskEditor;
  private final UserService userService;

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
      UUID projectId, UUID userId, String title, String role, String priority) {
    UUID workspaceId = requireProjectMember(projectId, userId);
    return taskCreator.create(
        CreateTaskCommand.manual(projectId, workspaceId, title, role, priority));
  }

  @Transactional(readOnly = true)
  public MobileTaskDetail getTaskDetail(UUID taskId, UUID userId) {
    TaskDetail detail =
        taskReader
            .findDetail(taskId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ProjectErrorCode.TASK_NOT_FOUND, Map.of("task_id", taskId.toString())));
    // 태스크가 속한 workspace는 상세가 들고 있으므로(레거시 WorkspaceForTask와 같은 해석) 멤버십만 확인한다.
    if (!workspaceAccess.isMember(detail.workspaceId(), userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("task_id", taskId.toString()));
    }
    return toMobileDetail(detail);
  }

  @Transactional
  public void updateTask(
      UUID taskId,
      UUID userId,
      String title,
      String role,
      UUID assigneeId,
      String priority,
      String status,
      String purpose,
      List<ChecklistEdit> checklistItems) {
    requireTaskMember(taskId, userId);
    List<UpdateTaskCommand.ChecklistItemEdit> items =
        checklistItems.stream()
            .map(
                edit ->
                    new UpdateTaskCommand.ChecklistItemEdit(
                        edit.id(), edit.title(), edit.completed()))
            .toList();
    // 저장만 하고 상세는 반환하지 않는다. 저장 후 최신 상태는 클라이언트가 상세 조회로 다시 읽는다.
    taskEditor.update(
        new UpdateTaskCommand(taskId, title, role, assigneeId, priority, status, purpose, items));
  }

  @Transactional
  public MobileTaskDetail toggleChecklistItem(
      UUID taskId, UUID userId, UUID itemId, boolean completed) {
    requireTaskMember(taskId, userId);
    TaskDetail updated = taskEditor.toggleChecklistItem(taskId, itemId, completed);
    return toMobileDetail(updated);
  }

  private void requireTaskMember(UUID taskId, UUID userId) {
    // 수정 전에는 상세 전체를 읽을 필요가 없어 workspace만 조회해 멤버십을 확인한다.
    UUID workspaceId =
        taskReader
            .workspaceIdOf(taskId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ProjectErrorCode.TASK_NOT_FOUND, Map.of("task_id", taskId.toString())));
    if (!workspaceAccess.isMember(workspaceId, userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("task_id", taskId.toString()));
    }
  }

  private MobileTaskDetail toMobileDetail(TaskDetail detail) {
    return new MobileTaskDetail(
        detail.id(),
        detail.projectId(),
        detail.title(),
        detail.status(),
        detail.role(),
        toAssignee(detail.assigneeId()),
        MobilePriority.fromStored(detail.priority()).key(),
        detail.description(),
        detail.checklistItems());
  }

  private MobileTaskDetail.Assignee toAssignee(UUID assigneeId) {
    if (assigneeId == null) {
      return null;
    }
    // assignee_id는 users FK(ON DELETE SET NULL)라 값이 있으면 항상 해석된다. 불변식이 깨지면 조용히
    // 담당자 없음으로 만들지 않고 getProfile의 USER_NOT_FOUND로 크게 실패한다.
    UserProfile profile = userService.getProfile(assigneeId);
    return new MobileTaskDetail.Assignee(profile.id(), profile.name(), profile.avatarUrl());
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
        task.id(), task.title(), task.role(), MobilePriority.fromStored(task.priority()).key(), 0);
  }
}
