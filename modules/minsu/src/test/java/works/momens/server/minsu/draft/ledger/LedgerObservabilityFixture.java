package works.momens.server.minsu.draft.ledger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 원장 협력자를 만드는 테스트가 지표 의존을 채우는 자리.
 *
 * <p>슬라이스 테스트는 필요한 빈만 {@code @Import}하므로 {@code MeterRegistry}가 컨텍스트에 없다. 지표를 검증하지 않는 테스트까지 그 사실을 알
 * 필요는 없어서 여기로 모은다.
 *
 * <p>지표 자체를 보는 테스트는 이걸 쓰지 않고 자기 {@link SimpleMeterRegistry}를 들고 직접 생성한다. 하나를 공유하면 테스트 간 카운터가 섞인다.
 */
@TestConfiguration(proxyBeanMethods = false)
class LedgerObservabilityFixture {

  static MinsuLedgerObservability observability() {
    return new MinsuLedgerObservability(new SimpleMeterRegistry());
  }

  @Bean
  MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  MinsuLedgerObservability minsuLedgerObservability(MeterRegistry meterRegistry) {
    return new MinsuLedgerObservability(meterRegistry);
  }
}
