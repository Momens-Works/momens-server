package works.momens.server.minsu.internal.llm;

public interface LlmClient {

  LlmResponse generate(ModelSelection selection, LlmRequest request);
}
