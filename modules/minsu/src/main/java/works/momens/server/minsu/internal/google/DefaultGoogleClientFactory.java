package works.momens.server.minsu.internal.google;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import java.time.Duration;
import org.springframework.stereotype.Component;
import works.momens.server.minsu.internal.config.MinsuLlmProperties;
import works.momens.server.minsu.internal.llm.ModelSelection;

@Component
final class DefaultGoogleClientFactory implements GoogleClientFactory {

  private final MinsuLlmProperties properties;

  DefaultGoogleClientFactory(MinsuLlmProperties properties) {
    this.properties = properties;
  }

  @Override
  public GoogleSdkClient create(ModelSelection selection) {
    Client client =
        Client.builder()
            .project(selection.project())
            .location(selection.location())
            .enterprise(true)
            .httpOptions(httpOptions(properties.timeout()))
            .build();
    return new DefaultGoogleSdkClient(client);
  }

  static HttpOptions httpOptions(Duration timeout) {
    return HttpOptions.builder()
        .apiVersion("v1")
        .timeout(Math.toIntExact(timeout.toMillis()))
        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
        .build();
  }
}
