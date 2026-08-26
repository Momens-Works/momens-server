package works.momens.server.project.task;

import java.time.LocalDate;
import java.util.UUID;

/** 일부 필드만 바꾸는 태스크 수정 입력. 각 {@code *Set} 값은 해당 필드 변경 여부를 뜻합니다. */
public record PatchTaskCommand(
    UUID taskId,
    String title,
    boolean titleSet,
    String description,
    boolean descriptionSet,
    String status,
    boolean statusSet,
    String priority,
    boolean prioritySet,
    UUID milestoneId,
    boolean milestoneSet,
    UUID assigneeId,
    boolean assigneeSet,
    LocalDate dueDate,
    boolean dueDateSet) {}
