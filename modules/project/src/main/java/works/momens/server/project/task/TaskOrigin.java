package works.momens.server.project.task;

/** 태스크 생성 출처(CO-6). 사람이 직접 만든 태스크와 Signal을 수용해 만든 태스크의 의미를 구분한다. */
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
