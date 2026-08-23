package works.momens.server.project.task;

import java.util.UUID;

/**
 * 조건부 draft 반영 요청.
 *
 * @param baseline 반영을 허용하는 현재 값. {@code tasks}가 이 값 그대로일 때만 갱신합니다
 * @param draft 반영할 값
 */
public record ApplyTaskDraftCommand(UUID taskId, TaskDraftValues baseline, TaskDraftValues draft) {}
