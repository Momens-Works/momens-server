package works.momens.server.source;

import java.util.UUID;

/**
 * source 연결을 시작할 워크스페이스와 요청자, 연결할 provider 이름을 담습니다.
 *
 * <p>provider 이름은 대소문자를 구분하지 않습니다.
 */
public record BeginInstallCommand(UUID workspaceId, UUID userId, String provider) {}
