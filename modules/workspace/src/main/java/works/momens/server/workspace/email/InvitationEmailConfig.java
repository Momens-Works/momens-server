package works.momens.server.workspace.email;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 워크스페이스 초대 이메일 발송에 필요한 구성 요소를 등록합니다.
 *
 * <p>이메일 발송 provider를 지정하지 않으면 이메일을 발송하지 않는 구현을 등록합니다. Resend를 지정한 경우에만 발송에 필요한 설정값을 요구하며, 필수 값이 비어
 * 있으면 서버 기동 시점에 실패시킵니다.
 *
 * <p>레거시는 운영 환경이 아니면 설정 누락에 대한 경고만 남기고 이메일을 발송하지 않습니다. 신규 서버는 이메일이 발송되지 않는 상태로 조용히 동작하는 것보다 설정 오류를
 * 기동 시점에 드러내는 편이 적절하다고 판단해 이 부분만 다르게 처리합니다.
 */
@Configuration
@EnableConfigurationProperties(InvitationEmailProperties.class)
class InvitationEmailConfig {

  private static final String PROVIDER_PROPERTY = "momens.workspace.invitation.email.provider";
  private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

  @Bean
  InvitationEmailSender invitationEmailSender(
      EmailClient emailClient, InvitationEmailProperties properties) {
    return new InvitationEmailSenderImpl(emailClient, properties);
  }

  @Bean
  @ConditionalOnProperty(name = PROVIDER_PROPERTY, havingValue = "noop", matchIfMissing = true)
  EmailClient noopEmailClient() {
    return new NoopEmailClient();
  }

  @Configuration
  @ConditionalOnProperty(name = PROVIDER_PROPERTY, havingValue = "resend")
  @EnableConfigurationProperties(ResendEmailProperties.class)
  static class ResendEmailConfig {

    @Bean
    EmailClient resendEmailClient(
        RestClient.Builder restClientBuilder,
        InvitationEmailProperties properties,
        ResendEmailProperties resendProperties) {
      requireConfigured(properties.acceptUrl(), "momens.workspace.invitation.email.accept-url");
      requireConfigured(properties.from(), "momens.workspace.invitation.email.from");
      requireConfigured(properties.replyTo(), "momens.workspace.invitation.email.reply-to");
      RestClient restClient =
          restClientBuilder
              .baseUrl(RESEND_ENDPOINT)
              .defaultHeader("Authorization", "Bearer " + resendProperties.apiKey())
              .requestFactory(requestFactory())
              .build();
      return new ResendEmailClient(restClient, properties.from(), properties.replyTo());
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
      SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
      requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
      requestFactory.setReadTimeout(READ_TIMEOUT);
      return requestFactory;
    }

    private static void requireConfigured(String value, String property) {
      if (value == null || value.isBlank()) {
        throw new IllegalStateException(
            "%s는 %s=resend일 때 반드시 설정해야 합니다.".formatted(property, PROVIDER_PROPERTY));
      }
    }
  }
}
