package works.momens.server.minsu.llm;

public record LlmResponse(
    boolean candidatePresent,
    String finishReason,
    String text,
    String responseId,
    TokenUsage tokenUsage) {

  public record TokenUsage(int prompt, int candidate, int thoughts, int total) {
    public static final TokenUsage EMPTY = new TokenUsage(0, 0, 0, 0);
  }
}
