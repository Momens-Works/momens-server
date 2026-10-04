package works.momens.server.source.connection.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Figma webhook lifecycle의 HTTP 경계입니다. provider 응답 본문은 오류나 로그에 포함하지 않습니다. */
@RequiredArgsConstructor
class FigmaWebhookClient {

  private final RestClient restClient;

  int delete(String accessToken, String webhookId) {
    return restClient
        .delete()
        .uri("/v2/webhooks/{id}", webhookId)
        .headers(headers -> headers.setBearerAuth(accessToken))
        .accept(MediaType.APPLICATION_JSON)
        .exchange((request, response) -> response.getStatusCode().value());
  }
}
