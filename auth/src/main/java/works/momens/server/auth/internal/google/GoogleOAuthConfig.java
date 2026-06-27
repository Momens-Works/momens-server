package works.momens.server.auth.internal.google;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 웹 Authorization Code 플로우에서 Google token/userinfo 엔드포인트를 호출하는 RestClient.
 *
 * <p>Boot가 auto-configure한 {@code RestClient.Builder}가 있으면 그것으로 만들어 trace context 전파·공통 커스터마이저를
 * 유지하고, 없는 컨텍스트에서는 기본 builder로 폴백합니다(docs/rules/observability.md). 외부 호출이 요청 스레드를 오래 붙잡지 않도록
 * connect/read 타임아웃을 고정합니다.
 */
@Configuration
class GoogleOAuthConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  @Bean
  RestClient googleOAuthRestClient(ObjectProvider<RestClient.Builder> builderProvider) {
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    requestFactory.setReadTimeout(READ_TIMEOUT);
    return builderProvider
        .getIfAvailable(RestClient::builder)
        .requestFactory(requestFactory)
        .build();
  }
}
