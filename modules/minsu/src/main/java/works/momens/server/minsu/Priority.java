package works.momens.server.minsu;

import java.util.Arrays;
import java.util.Optional;

/** Task priority의 공개 값과 wire/storage 표현. */
public enum Priority {
  LOW("low"),
  MEDIUM("medium"),
  HIGH("high");

  private final String value;

  Priority(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static Optional<Priority> fromValue(String value) {
    return Arrays.stream(values()).filter(priority -> priority.value.equals(value)).findFirst();
  }
}
