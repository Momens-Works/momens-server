package works.momens.server.project.milestone;

import java.util.Optional;

/** milestone 경계가 소유하는 {@code health_status} 저장값. */
enum HealthStatus {
  ON_TRACK("on_track"),
  AT_RISK("at_risk"),
  BLOCKED("blocked"),
  PLANNED("planned"),
  OPEN("open");

  private final String value;

  HealthStatus(String value) {
    this.value = value;
  }

  static Optional<HealthStatus> from(String value) {
    for (HealthStatus status : values()) {
      if (status.value.equals(value)) {
        return Optional.of(status);
      }
    }
    return Optional.empty();
  }

  String value() {
    return value;
  }
}
