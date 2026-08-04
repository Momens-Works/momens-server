package works.momens.server.minsu.internal.google;

import java.time.Duration;
import works.momens.server.minsu.internal.llm.LlmRequest;
import works.momens.server.minsu.internal.llm.LlmResponse;
import works.momens.server.minsu.internal.llm.ModelSelection;

interface GoogleSdkClient extends AutoCloseable {

  LlmResponse generate(ModelSelection selection, LlmRequest request, Duration timeout);

  @Override
  void close();
}
