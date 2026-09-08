package works.momens.server.project.taskupdate.internal;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.common.api.FieldValidationException;
import works.momens.server.project.task.TaskErrorCode;
import works.momens.server.project.task.TaskReader;
import works.momens.server.project.task.TaskScope;
import works.momens.server.project.taskupdate.TaskUpdateDetail;
import works.momens.server.project.taskupdate.TaskUpdateWriter;

@Service
@RequiredArgsConstructor
class TaskUpdateWriterImpl implements TaskUpdateWriter {

  private final TaskUpdateRepository taskUpdateRepository;
  private final TaskReader taskReader;

  @Override
  @Transactional
  public TaskUpdateDetail create(
      UUID taskId, UUID userId, String body, String kind, Map<String, Object> metadata) {
    TaskScope task = requireTask(taskId);
    if (body == null || body.trim().isEmpty()) {
      throw FieldValidationException.forField("body");
    }
    TaskUpdate update =
        TaskUpdate.create(
            task.workspaceId(),
            task.projectId(),
            taskId,
            userId,
            body.trim(),
            normalizeKind(kind),
            metadata);
    taskUpdateRepository.save(update);
    return update.toDetail();
  }

  @Override
  @Transactional
  public void delete(UUID taskId, UUID updateId, UUID userId) {
    // 태스크가 소프트 삭제된 경우 업데이트를 삭제하지 않도록 태스크의 삭제 여부만 확인합니다.
    requireTask(taskId);
    TaskUpdate update =
        taskUpdateRepository
            .findByIdAndTaskIdAndDeletedAtIsNull(updateId, taskId)
            .orElseThrow(this::taskNotFound);
    if (!userId.equals(update.getAuthorId())) {
      throw new BusinessException(CommonErrorCode.AUTH_FORBIDDEN);
    }
    update.delete();
  }

  private TaskScope requireTask(UUID taskId) {
    return taskReader.findScope(taskId).orElseThrow(this::taskNotFound);
  }

  private BusinessException taskNotFound() {
    return new BusinessException(TaskErrorCode.TASK_NOT_FOUND);
  }

  private static String normalizeKind(String value) {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "", "comment" -> "comment";
      case "update" -> "update";
      default -> throw FieldValidationException.forField("kind");
    };
  }
}
