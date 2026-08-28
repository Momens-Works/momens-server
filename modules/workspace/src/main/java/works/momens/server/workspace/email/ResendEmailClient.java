package works.momens.server.workspace.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Resend를 통해 이메일을 발송합니다.
 *
 * <p>발신 주소와 회신 주소는 요청마다 달라지지 않으므로 설정값으로 관리합니다. Resend 호출에 실패하면 원인을 포함한 {@link
 * EmailSendFailedException}으로 변환해 던지며, 초대 하위 도메인은 이를 502 응답으로 변환합니다.
 */
class ResendEmailClient implements EmailClient {

  private final RestClient restClient;
  private final String from;
  private final String replyTo;

  ResendEmailClient(RestClient restClient, String from, String replyTo) {
    this.restClient = restClient;
    this.from = from;
    this.replyTo = replyTo;
  }

  @Override
  public void send(EmailMessage message) {
    try {
      restClient
          .post()
          .body(
              new ResendEmailPayload(
                  from,
                  List.of(message.to()),
                  message.subject(),
                  message.html(),
                  message.text(),
                  replyTo))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw new EmailSendFailedException("Resend를 통한 이메일 발송에 실패했습니다.", e);
    }
  }

  private record ResendEmailPayload(
      String from,
      List<String> to,
      String subject,
      String html,
      String text,
      @JsonProperty("reply_to") String replyTo) {}
}
