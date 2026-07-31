package works.momens.server.notification.fcm;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Firebase Admin SDK가 FCM 요청을 보낼 대상 프로젝트 설정. */
@Validated
@ConfigurationProperties(prefix = "momens.notification.push.firebase")
record FirebaseFcmProperties(@NotBlank String projectId) {}
