package works.momens.server.source.connection.oauth;

import java.util.UUID;

/**
 * source 승인 흐름을 시작한 워크스페이스, 사용자, provider 정보를 담습니다.
 *
 * <p>이 값은 서명된 문자열로 변환되어 provider에 전달되고, provider가 신규 서버로 보내는 콜백 요청에 다시 포함됩니다. 콜백 요청에는 로그인 정보가 없으므로
 * 누가 어느 워크스페이스에 어떤 provider를 연결하려 했는지는 이 값으로 판정합니다.
 */
record OAuthState(UUID workspaceId, UUID userId, String provider) {}
