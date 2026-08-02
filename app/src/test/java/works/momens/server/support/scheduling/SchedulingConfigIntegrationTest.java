package works.momens.server.support.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

/**
 * push 비활성 기본 배선(local·test)에서도 {@code @Scheduled} 빈이 실제로 실행되는지 검증합니다(MOM-0816).
 *
 * <p>게이트를 notification이 소유하던 때는 {@code momens.notification.push.enabled=false}면 이 앱이 소유한 활성화가 통째로
 * 빠졌습니다. 다른 모듈의 스케줄러가 push 설정에 종속되지 않는다는 것이 이 테스트가 지키는 계약입니다.
 */
@SpringBootTest
class SchedulingConfigIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CountDownLatch scheduledInvocations;

  @Test
  @DisplayName("push 비활성 환경에서도 다른 모듈의 @Scheduled 빈이 실행된다")
  void runsScheduledBeanWithPushDisabled() throws InterruptedException {
    assertThat(scheduledInvocations.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @TestConfiguration
  static class ProbeConfig {

    private final CountDownLatch latch = new CountDownLatch(1);

    @Bean
    CountDownLatch scheduledInvocations() {
      return latch;
    }

    @Bean
    ScheduledProbe scheduledProbe() {
      return new ScheduledProbe(latch);
    }
  }

  record ScheduledProbe(CountDownLatch latch) {

    @Scheduled(fixedDelayString = "100ms")
    void tick() {
      latch.countDown();
    }
  }
}
