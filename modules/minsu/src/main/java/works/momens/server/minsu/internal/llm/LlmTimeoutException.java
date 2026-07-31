package works.momens.server.minsu.internal.llm;

public final class LlmTimeoutException extends RuntimeException {

  public LlmTimeoutException(Throwable cause) {
    super("LLM request timed out", cause);
  }
}
