package works.momens.server.minsu.internal.google;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.stereotype.Component;
import works.momens.server.minsu.internal.llm.ModelSelection;

@Component
final class DefaultGoogleClientFactory implements GoogleClientFactory {

  @Override
  public GoogleSdkClient create(ModelSelection selection) {
    Client client =
        Client.builder()
            .project(selection.project())
            .location(selection.location())
            .enterprise(true)
            .httpOptions(httpOptions())
            .build();
    return new DefaultGoogleSdkClient(client);
  }

  static HttpOptions httpOptions() {
    return HttpOptions.builder()
        .apiVersion("v1")
        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
        .build();
  }
}
