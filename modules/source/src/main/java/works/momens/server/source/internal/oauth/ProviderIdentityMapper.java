package works.momens.server.source.internal.oauth;

import java.util.Map;

/**
 * 토큰 응답과 사용자 정보 응답에서 외부 계정 정보를 추출합니다.
 *
 * <p>provider마다 외부 계정 정보가 저장된 위치와 필드 이름이 다르므로 이 변환 로직만 provider별로 구현합니다.
 */
@FunctionalInterface
interface ProviderIdentityMapper {

  ProviderIdentity map(Map<String, Object> tokenResponse, Map<String, Object> identityBody);
}
