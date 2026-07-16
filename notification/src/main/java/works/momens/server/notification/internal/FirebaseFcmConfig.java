package works.momens.server.notification.internal;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Firebase Admin SDK 초기화(docs/design/signal-push-demo-design.md 12절).
 *
 * <p>인증 정보는 Application Default Credentials로 읽는다. dev 배포는 서비스 계정 JSON을 Kubernetes Secret으로 파일 마운트하고
 * {@code GOOGLE_APPLICATION_CREDENTIALS}에 경로를 지정한다. 배포 환경이 Workload Identity를 제공하게 되면 초기화 코드는 유지한 채
 * 인증 정보 공급 방식만 교체한다.
 */
@Configuration
@ConditionalOnProperty(name = "momens.notification.push.enabled", havingValue = "true")
class FirebaseFcmConfig {

  @Bean
  FirebaseMessaging firebaseMessaging() throws IOException {
    FirebaseApp app =
        FirebaseApp.getApps().isEmpty()
            ? FirebaseApp.initializeApp(
                FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build())
            : FirebaseApp.getInstance();
    return FirebaseMessaging.getInstance(app);
  }
}
