package works.momens.server.source.connection.oauth;

import java.util.List;
import java.util.Map;

/**
 * provider 한 곳에 적용되는 고정 규칙을 정의합니다.
 *
 * <p>승인 URL, 토큰 교환 URL, scope, 요청 형식, 사용자 정보 조회 방식을 값으로 포함합니다.
 *
 * <p><strong>provider별 차이를 값으로 표현한 이유가 있습니다.</strong> 네 provider의 토큰 교환 방식은 두 가지로 구분됩니다. 요청 본문이
 * form인지 JSON인지, 자격 증명을 요청 본문에 넣는지 {@code Authorization} 헤더에 넣는지입니다. 두 규칙을 이 타입에 정의하면 토큰 교환 로직은 하나로
 * 유지할 수 있습니다. provider별로 별도 구현이 필요한 부분은 응답에서 외부 계정 식별자와 이름을 추출하는 방식뿐이므로 해당 로직만 함수로 전달받습니다.
 *
 * <p>따라서 provider를 추가할 때는 이 정의 한 건과 외부 계정 정보를 추출하는 함수 하나만 추가하면 됩니다.
 */
record ProviderDefinition(
    String sourceType,
    String authorizeEndpoint,
    String tokenEndpoint,
    List<String> scopes,
    Map<String, String> authorizeParams,
    TokenBodyFormat tokenBodyFormat,
    ClientAuthStyle clientAuthStyle,
    String identityEndpoint,
    IdentityMethod identityMethod,
    ProviderIdentityMapper identityMapper) {

  enum TokenBodyFormat {
    FORM,
    JSON
  }

  enum ClientAuthStyle {
    REQUEST_BODY,
    BASIC_HEADER
  }

  enum IdentityMethod {
    NONE,
    GET,
    POST
  }
}
