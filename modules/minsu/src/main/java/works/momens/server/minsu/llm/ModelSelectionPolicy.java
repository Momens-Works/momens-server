package works.momens.server.minsu.llm;

public interface ModelSelectionPolicy {

  ModelSelection select(LlmUseCase useCase);
}
