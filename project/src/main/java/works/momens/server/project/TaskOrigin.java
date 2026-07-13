package works.momens.server.project;

/**
 * 태스크 출처.
 *
 * <p>사람이 직접 만든 태스크({@code manual})와 Signal/Minsu 제안을 사람이 수용해 만든 태스크({@code signal})를 의미로
 * 구분한다(CO-6). {@code value()}는 {@code tasks.origin_type} 컬럼과 {@code task.created} 이벤트 payload에 쓰는
 * 문자열이다.
 */
public enum TaskOrigin {
  MANUAL("manual"),
  SIGNAL("signal");

  private final String value;

  TaskOrigin(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
