package works.momens.server.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

/**
 * push 비활성 환경(local·test 기본 배선)에서 폴링 스케줄러와 Firebase adapter가 빠지는지 검증합니다. 스케줄링 활성화가 app으로 올라간 뒤에도
 * (MOM-0816) 폴링이 멈추는 것은 이 빈들이 등록되지 않기 때문입니다. 대상 빈이 nested 모듈에 package-private로 은닉돼 있어 bean 이름으로
 * 확인합니다.
 */
@SpringBootTest
class PushDisabledWiringIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("push 비활성 프로퍼티가 폴링 스케줄러와 Firebase adapter를 끈다")
  void disablesSchedulerAndFirebaseAdapter() {
    assertThat(context.containsBean("notificationPushScheduler")).isFalse();
    assertThat(context.containsBean("firebaseFcmClient")).isFalse();
    assertThat(context.containsBean("disabledFcmClient")).isTrue();
  }
}
