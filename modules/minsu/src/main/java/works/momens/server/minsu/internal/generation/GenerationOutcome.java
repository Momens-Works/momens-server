package works.momens.server.minsu.internal.generation;

enum GenerationOutcome {
  DISABLED("fallback", "disabled"),
  /** 비동기 활성. 요청 경로에서는 고정 fallback만 돌려주고 실제 생성은 원장을 통해 scheduler가 맡는다(5.5절). */
  DEFERRED("fallback", "async_deferred"),
  INVALID_CONFIG("fallback", "invalid_config"),
  INSUFFICIENT_CONTEXT("fallback", "insufficient_context"),
  TIMEOUT("fallback", "timeout"),
  PROVIDER_ERROR("fallback", "provider_error"),
  INVALID_RESPONSE("fallback", "invalid_response"),
  INVALID_OUTPUT("fallback", "invalid_output"),
  GENERATED_TITLE_FALLBACK("generated_title_fallback", "none"),
  GENERATED_TRUNCATED("generated_truncated", "none"),
  GENERATED("generated", "none");

  private final String outcome;
  private final String reason;

  GenerationOutcome(String outcome, String reason) {
    this.outcome = outcome;
    this.reason = reason;
  }

  String outcome() {
    return outcome;
  }

  String reason() {
    return reason;
  }
}
