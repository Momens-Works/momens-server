package works.momens.server.minsu.internal.google;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import works.momens.server.minsu.internal.llm.LlmClient;
import works.momens.server.minsu.internal.llm.LlmRequest;
import works.momens.server.minsu.internal.llm.LlmResponse;
import works.momens.server.minsu.internal.llm.ModelSelection;

@Component
final class GoogleGenAiLlmClient implements LlmClient {

  private final GoogleClientFactory clientFactory;
  private final Object monitor = new Object();
  private volatile GoogleSdkClient cachedClient;

  GoogleGenAiLlmClient(GoogleClientFactory clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public LlmResponse generate(ModelSelection selection, LlmRequest request) {
    return client(selection).generate(selection, request);
  }

  @PreDestroy
  void close() {
    GoogleSdkClient client = cachedClient;
    if (client != null) {
      client.close();
    }
  }

  private GoogleSdkClient client(ModelSelection selection) {
    GoogleSdkClient client = cachedClient;
    if (client != null) {
      return client;
    }
    synchronized (monitor) {
      client = cachedClient;
      if (client == null) {
        client = clientFactory.create(selection);
        cachedClient = client;
      }
      return client;
    }
  }
}
