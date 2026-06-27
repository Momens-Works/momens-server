package works.momens.server.auth.internal.google;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 웹 Authorization Code 플로우에서 Google token/userinfo 엔드포인트를 호출하는 RestClient.
 *
 * <p>Boot가 auto-configure한 {@code RestClient.Builder}가 있으면 그것으로 만들어 trace context 전파·공통 커스터마이저를
 * 유지하고, 없는 컨텍스트에서는 기본 builder로 폴백합니다(docs/rules/observability.md).
 */
@Configuration
class GoogleOAuthConfig {

  @Bean
  RestClient googleOAuthRestClient(ObjectProvider<RestClient.Builder> builderProvider) {
    return builderProvider.getIfAvailable(RestClient::builder).build();
  }
}
