package works.momens.server.source.internal.oauth;

import java.util.Map;

/**
 * provider에서 조회한 외부 계정 정보를 나타냅니다.
 *
 * <p>식별자는 외부 계정을 다시 승인했을 때 기존 연결과 같은 연결로 볼지 판정하는 기준이므로, 해당 provider 안에서 변경되지 않는 값을 사용합니다.
 */
record ProviderIdentity(String externalId, String externalName, Map<String, Object> metadata) {}
