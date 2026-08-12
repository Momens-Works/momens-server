package works.momens.server.minsu.draft.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("momens.minsu.task-draft")
public record MinsuTaskDraftProperties(
    @DefaultValue("false") boolean enabled, @DefaultValue Metrics metrics) {

  /**
   * 원장 지표 스냅샷 정책(MOM-0821).
   *
   * <p>비동기 축과 무관하게 동작하므로 {@code async} 아래가 아니다. drain을 끈 뒤 남은 {@code pending}을 보는 것이 이 지표의 핵심 용도다.
   *
   * @param snapshotInterval 집계를 다시 돌리는 주기. 이 값이 곧 gauge가 낡을 수 있는 상한이다
   */
  public record Metrics(@DefaultValue("10s") Duration snapshotInterval) {

    public Metrics {
      if (snapshotInterval.isNegative() || snapshotInterval.isZero()) {
        throw new IllegalArgumentException(
            "momens.minsu.task-draft.metrics.snapshot-interval은 0보다 커야 합니다");
      }
    }
  }
}
