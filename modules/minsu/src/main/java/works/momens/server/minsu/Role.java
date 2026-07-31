package works.momens.server.minsu;

import java.util.Arrays;
import java.util.Optional;

/** Task role의 공개 값과 wire/storage 표현. */
public enum Role {
  PM("pm"),
  DESIGN("design"),
  BACKEND("backend"),
  FRONTEND("frontend");

  private final String value;

  Role(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static Optional<Role> fromValue(String value) {
    return Arrays.stream(values()).filter(role -> role.value.equals(value)).findFirst();
  }
}
