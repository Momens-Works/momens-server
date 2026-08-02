package works.momens.server.minsu.internal.ledger;

/**
 * 원장 종료 사유(docs/design/minsu-async-task-draft-design.md 7.1절).
 *
 * <p>사유는 원장과 관측에만 남고 API로 노출하지 않는다. 앱에게 필요한 정보는 "이 title이 앞으로 더 바뀌는가" 하나이고 그 점에서 모든 종료 사유는 같다.
 *
 * <p>설정 비활성은 사유가 아니다(11.2절). 되돌리면 계속 처리할 수 있는 작업이므로 원장을 {@code pending} 그대로 두고, 운영자가 명시적으로 끝내는 경우만
 * {@link #OPERATIONALLY_CLOSED}로 기록한다.
 */
enum CompletionReason {
  /** 모델 draft를 tasks에 반영했다. */
  GENERATED("generated"),
  /** 사용자가 먼저 편집해 결과를 폐기했다(8.1절). */
  USER_EDITED("user_edited"),
  /** 대상 task가 삭제됐다(8.1절). */
  TASK_GONE("task_gone"),
  /** 운영 판단으로 수동 종료했다(11.1절). */
  OPERATIONALLY_CLOSED("operationally_closed"),
  /** 원장 나이 상한을 초과했다(8.6절). */
  DEADLINE_EXCEEDED("deadline_exceeded"),
  /** 입력이 부족해 재시도 없이 종료했다. */
  INSUFFICIENT_CONTEXT("insufficient_context"),
  /** 설정이 무효해 종료했다. */
  INVALID_CONFIG("invalid_config"),
  /** 재시도 상한에 도달했다. */
  RETRY_EXHAUSTED("retry_exhausted");

  private final String value;

  CompletionReason(String value) {
    this.value = value;
  }

  String value() {
    return value;
  }
}
