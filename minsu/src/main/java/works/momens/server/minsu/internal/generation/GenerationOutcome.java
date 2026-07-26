package works.momens.server.minsu.internal.generation;

enum GenerationOutcome {
  DISABLED("fallback", "disabled"),
  INVALID_CONFIG("fallback", "invalid_config"),
  INSUFFICIENT_CONTEXT("fallback", "insufficient_context"),
  PROVIDER_ERROR("fallback", "provider_error"),
  INVALID_RESPONSE("fallback", "invalid_response"),
  INVALID_OUTPUT("fallback", "invalid_output"),
  GENERATED_TITLE_FALLBACK("generated", "generated_title_fallback"),
  GENERATED_TRUNCATED("generated", "generated_truncated"),
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
