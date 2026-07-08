package works.momens.server.signal.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 상대 시각 라벨 구간 검증. */
class RelativeTimeLabelTest {

  private static final Instant NOW = Instant.parse("2026-07-07T00:00:00Z");

  @Test
  @DisplayName("occurred_at이 null이면 라벨도 null이다")
  void nullWhenOccurredAtMissing() {
    assertThat(RelativeTimeLabel.of(NOW, null)).isNull();
  }

  @Test
  @DisplayName("구간별로 방금 전/분/시간/일 라벨을 만든다")
  void formatsEachBucket() {
    assertThat(RelativeTimeLabel.of(NOW, NOW.minus(Duration.ofSeconds(30)))).isEqualTo("방금 전");
    assertThat(RelativeTimeLabel.of(NOW, NOW.minus(Duration.ofMinutes(5)))).isEqualTo("5분 전");
    assertThat(RelativeTimeLabel.of(NOW, NOW.minus(Duration.ofHours(3)))).isEqualTo("3시간 전");
    assertThat(RelativeTimeLabel.of(NOW, NOW.minus(Duration.ofDays(2)))).isEqualTo("2일 전");
  }

  @Test
  @DisplayName("미래 시각은 방금 전으로 처리한다")
  void futureIsJustNow() {
    assertThat(RelativeTimeLabel.of(NOW, NOW.plus(Duration.ofMinutes(10)))).isEqualTo("방금 전");
  }
}
