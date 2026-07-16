package works.momens.server.notification.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

/**
 * push 활성 환경({@code momens.notification.push.enabled=true}, dev 배포와 같은 배선)에서 폴링 스케줄러·스케줄링
 * 설정·Firebase adapter가 등록되고 비활성 배선(DisabledFcmClient)은 빠지는지 검증합니다. Firebase 초기화(ADC)만 mock으로 대체합니다.
 */
@SpringBootTest(properties = "momens.notification.push.enabled=true")
class PushEnabledWiringIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private ApplicationContext context;
  @MockitoBean private FirebaseMessaging firebaseMessaging;

  @Test
  @DisplayName("push 활성 프로퍼티가 폴링 스케줄러와 Firebase adapter를 켠다")
  void enablesSchedulerAndFirebaseAdapter() {
    assertThat(context.getBeanNamesForType(NotificationPushScheduler.class)).hasSize(1);
    assertThat(context.getBeanNamesForType(NotificationSchedulingConfig.class)).hasSize(1);
    assertThat(context.getBean(FcmClient.class)).isInstanceOf(FirebaseFcmClient.class);
    assertThat(context.getBeanNamesForType(DisabledFcmClient.class)).isEmpty();
  }
}
