package works.momens.server.minsu.llm;

final class DeploymentModelSelectionPolicy implements ModelSelectionPolicy {

  private final MinsuLlmProperties properties;

  DeploymentModelSelectionPolicy(MinsuLlmProperties properties) {
    this.properties = properties;
  }

  @Override
  public ModelSelection select(LlmUseCase useCase) {
    return switch (useCase) {
      case SIGNAL_TASK_DRAFT ->
          new ModelSelection(
              properties.provider(),
              properties.model(),
              properties.google().project(),
              properties.google().location());
      default -> throw new IllegalArgumentException("지원하지 않는 LLM use case입니다");
    };
  }
}
