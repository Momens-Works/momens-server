package works.momens.server.project;

/**
 * 조건부 draft 반영의 결과.
 *
 * <p>호출자가 이 값을 자기 도메인의 종료 사유로 옮깁니다. 여기에 {@code user_edited} 같은 이름을 두지 않는 이유는 {@code project}가 값이
 * 달라진 <b>이유</b>를 모르기 때문입니다. baseline과 다르다는 사실만 관측할 수 있고, 그것을 사용자 편집으로 해석하는 것은 baseline을 소유한 쪽의
 * 판단입니다.
 */
public enum TaskDraftApplyResult {
  /** baseline과 일치해 draft를 반영했습니다. */
  APPLIED,
  /** 현재 값이 baseline과 달라 아무것도 바꾸지 않았습니다. */
  BASELINE_MISMATCH,
  /** 대상 task가 없거나 삭제됐습니다. */
  TASK_GONE
}
