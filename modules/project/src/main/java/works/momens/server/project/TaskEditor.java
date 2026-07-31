package works.momens.server.project;

import java.util.UUID;

/**
 * task 수정 public API.
 *
 * <p>모바일 태스크 수정 화면(MOM-75)이 사용합니다. 태스크 본문 수정과 완료기준 항목 하나의 완료 상태 변경을 담당하고, 두 작업 모두 수정 후 상태를 {@link
 * TaskDetail}로 반환합니다. 접근 권한 검사는 호출하는 쪽(mobile)이 {@link TaskReader#workspaceIdOf(UUID)}로 workspace를
 * 확인한 뒤 호출합니다.
 */
public interface TaskEditor {

  /** 수정 화면이 저장한 편집 상태 전체로 태스크를 갱신하고, 갱신된 상태를 반환합니다. */
  TaskDetail update(UpdateTaskCommand command);

  /** 완료기준 항목 하나의 완료 상태를 변경하고, 갱신된 태스크 상태를 반환합니다. */
  TaskDetail toggleChecklistItem(UUID taskId, UUID itemId, boolean completed);
}
