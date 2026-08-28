package works.momens.server.project.milestone.internal;

import java.util.Optional;

/**
 * 마일스톤의 {@code health_status} 값입니다.
 *
 * <p>저장할 수 있는 값은 {@code milestones.health_status}의 CHECK 제약에서 허용하는 다섯 가지 값과 같습니다. project도 현재 같은
 * 문자열 집합을 사용하지만 별도 하위 도메인이므로 자기 경계 안에서 독립적으로 관리합니다.
 *
 * <p>마일스톤 기본값은 {@code planned}이며 생성 서비스에서 결정합니다.
 *
 * <p>enum 상수 이름과 DB 저장값이 다르므로 {@code value}를 별도로 관리합니다. {@code name()}을 저장값으로 사용하면 enum 상수 이름을 변경할
 * 때 DB에 저장되는 값도 함께 달라질 수 있습니다.
 *
 * <p>이 enum은 외부에 공개하지 않습니다. public API는 이 값을 문자열로 주고받으며, 레거시 응답과 웹 클라이언트도 문자열을 사용합니다. 따라서 enum의
 * 가시성은 허용값을 검증하는 구현 범위로 제한합니다.
 */
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
