package works.momens.server.notification.consume;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * push 활성 환경에서만 스케줄링 인프라를 켠다. 다른 모듈에는 아직 {@code @Scheduled} 빈이 없어 이 모듈이 게이트를 소유한다. push 비활성
 * 환경(local·test 기본)에서는 {@link NotificationPushScheduler}와 함께 등록되지 않는다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "momens.notification.push.enabled", havingValue = "true")
class NotificationSchedulingConfig {}
