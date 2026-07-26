package works.momens.server.minsu.internal.llm;

public interface ModelSelectionPolicy {

  ModelSelection select(LlmUseCase useCase);
}
