package works.momens.server.source.internal.oauth;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * source OAuth 연동에 필요한 구성 요소를 등록합니다.
 *
 * <p><strong>설정이 비어 있어도 서버는 정상적으로 기동되어야 합니다.</strong> 운영 환경에 provider 자격 증명을 주입하기 전에도 다른 기능은 동작해야
 * 하기 때문입니다. provider가 하나도 설정되지 않은 경우에는 state 서명과 토큰 암호화를 사용할 수 없는 상태로 구성하고, 실제 연결 흐름에서 해당 기능을 사용하려
 * 할 때 실패시킵니다. 레거시도 provider 자격 증명이 있을 때만 토큰 암호화 키를 읽습니다.
 *
 * <p>시각을 제공하는 {@link Clock}은 별도 구성 요소로 등록하지 않습니다. {@code :auth} 모듈에서도 같은 타입을 주입받으므로 {@code Clock}
 * 빈을 추가하면 인증 기능이 동작하지 않습니다.
 */
@Configuration
@EnableConfigurationProperties(SourceOAuthProperties.class)
class SourceOAuthConfig {

  @Bean
  OAuthProviderRegistry oauthProviderRegistry(SourceOAuthProperties properties) {
    return new OAuthProviderRegistry(properties);
  }

  @Bean
  OAuthStateSigner oauthStateSigner(SourceOAuthProperties properties) {
    return properties.hasAnyConfiguredProvider()
        ? new OAuthStateSigner(properties.stateSecret(), properties.stateTtl(), Clock.systemUTC())
        : OAuthStateSigner.unavailable();
  }

  @Bean
  TokenCipher sourceTokenCipher(SourceOAuthProperties properties) {
    return properties.hasAnyConfiguredProvider()
        ? new TokenCipher(properties.tokenKey())
        : TokenCipher.unavailable();
  }

  @Bean
  ProviderTokenExchanger providerTokenExchanger() {
    return new ProviderTokenExchanger(RestClient.builder().build());
  }
}
