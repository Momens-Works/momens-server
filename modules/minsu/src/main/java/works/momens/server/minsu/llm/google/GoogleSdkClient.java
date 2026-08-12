package works.momens.server.minsu.llm.google;

import java.time.Duration;
import works.momens.server.minsu.llm.LlmRequest;
import works.momens.server.minsu.llm.LlmResponse;
import works.momens.server.minsu.llm.ModelSelection;

interface GoogleSdkClient extends AutoCloseable {

  LlmResponse generate(ModelSelection selection, LlmRequest request, Duration timeout);

  @Override
  void close();
}
