package works.momens.server.source;

/**
 * 저장된 source 연결 정보와 승인 완료 후 사용자를 이동시킬 URL을 담습니다.
 *
 * <p>이동할 URL이 설정되지 않았으면 빈 값이며, 이 경우 호출하는 쪽에서 연결 정보를 응답합니다.
 */
public record CompletedInstall(SourceConnectionDetail connection, String successRedirectUri) {}
