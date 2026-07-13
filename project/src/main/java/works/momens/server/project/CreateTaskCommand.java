package works.momens.server.project;

import java.util.UUID;

/**
 * 태스크 생성 입력.
 *
 * <p>{@code workspaceId}는 호출하는 쪽(mobile)이 권한 검사 단계에서 이미 확정한 값을 넘깁니다. {@code role}과 {@code priority}
 * 검증은 표면(mobile)이 하고, 이 모듈은 저장과 라벨 발급을 책임집니다.
 *
 * <p>{@code origin}과 {@code originSignalId}는 태스크 출처를 보존합니다(CO-6). 사람이 직접 만든 태스크는 {@link #manual}로,
 * Signal을 수용해 만든 태스크는 {@link #fromSignal}로 만들어, signal 출처일 때만 원본 Signal id가 함께 담기도록 강제합니다.
 */
public record CreateTaskCommand(
    UUID projectId,
    UUID workspaceId,
    String title,
    String role,
    String priority,
    TaskOrigin origin,
    UUID originSignalId) {

  public CreateTaskCommand {
    // 팩토리를 우회한 canonical 생성자 호출도 출처 불변식을 지키도록 생성 시점에 강제한다(DB CHECK 이전 fail-fast).
    if (origin == TaskOrigin.SIGNAL && originSignalId == null) {
      throw new IllegalArgumentException("signal 출처는 originSignalId가 필요합니다");
    }
    if (origin == TaskOrigin.MANUAL && originSignalId != null) {
      throw new IllegalArgumentException("manual 출처는 originSignalId를 가질 수 없습니다");
    }
  }

  public static CreateTaskCommand manual(
      UUID projectId, UUID workspaceId, String title, String role, String priority) {
    return new CreateTaskCommand(
        projectId, workspaceId, title, role, priority, TaskOrigin.MANUAL, null);
  }

  public static CreateTaskCommand fromSignal(
      UUID projectId, UUID workspaceId, String title, String role, String priority, UUID signalId) {
    return new CreateTaskCommand(
        projectId, workspaceId, title, role, priority, TaskOrigin.SIGNAL, signalId);
  }
}
