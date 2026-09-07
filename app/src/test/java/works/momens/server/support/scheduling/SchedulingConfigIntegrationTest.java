package works.momens.server.support.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 *
 * <p>{@code @EnableScheduling}이 선언되어 있던 {@code MomentsAutoConfiguration}은 MOM-0921부터 런타임 클래스패스에
 * 없습니다({@link SchedulingConfig}의 Javadoc 참고). 따라서 이 테스트는 프로퍼티로 해당 자동 구성을 비활성화하지 않아도 {@code
 * SchedulingConfig}를 제거하면 실패합니다. Modulith 런타임 기능을 도입해 spring-modulith-moments가 다시 추가되면 {@code
 * spring.modulith.moments.enabled=false}를 이 애너테이션과 {@code application.yml}에 다시 설정해야 합니다.
 */
@SpringBootTest
class SchedulingConfigIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final CountDownLatch TICKS = new CountDownLatch(1);

  @Test
  @DisplayName("push 비활성 환경에서도 다른 모듈의 @Scheduled 빈이 실행된다")
  void runsScheduledBeanWithPushDisabled() throws InterruptedException {
    assertThat(TICKS.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @TestConfiguration
  static class ProbeConfig {

    @Bean
    ScheduledProbe scheduledProbe() {
      return new ScheduledProbe();
    }
  }

  static class ScheduledProbe {

    @Scheduled(fixedDelayString = "100ms")
    void tick() {
      TICKS.countDown();
    }
  }
}
