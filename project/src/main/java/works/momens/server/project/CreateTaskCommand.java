package works.momens.server.project;

import java.util.UUID;

/**
 * 태스크 생성 입력.
 *
 * <p>{@code workspaceId}는 호출하는 쪽(mobile)이 권한 검사 단계에서 이미 확정한 값을 넘깁니다. {@code role}과 {@code priority}
 * 검증은 표면(mobile)이 하고, 이 모듈은 저장과 라벨 발급을 책임집니다.
 */
public record CreateTaskCommand(
    UUID projectId, UUID workspaceId, String title, String role, String priority) {}
