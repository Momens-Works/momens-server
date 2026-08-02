package works.momens.server.support.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 인프라 활성화(MOM-0816).
 *
 * <p>인프라 활성화는 조립 모듈인 {@code app}이 소유하고 항상 켠다. {@code @Scheduled} 빈의 실행 여부는 각 모듈이 자신의 설정으로 판정한다(예:
 * notification의 폴링 스케줄러는 {@code momens.notification.push.enabled}로 등록 자체가 갈린다). 게이트를 특정 모듈이 소유하면 다른
 * 모듈의 스케줄러가 그 모듈의 설정에 우연히 종속되므로 여기로 올렸다.
 *
 * <p>사실 스케줄링은 이 클래스가 없어도 켜져 있었다. spring-modulith-starter-core의 {@code MomentsAutoConfiguration}이
 * 클래스 레벨 {@code @EnableScheduling}을 달고 있고, 그 조건이 {@code @ConditionalOnProperty(name =
 * "spring.modulith.moments.enabled", matchIfMissing = true)}라 프로퍼티를 지정하지 않은 기본값에서 활성화되기 때문이다. 그래서
 * push를 꺼도 다른 모듈의 스케줄러는 돌았다. 다만 그 보장이 무관한 라이브러리의 부수효과에 얹혀 있었다.
 *
 * <p>그래서 {@code application.yml}에서 {@code spring.modulith.moments.enabled=false}로 그 auto-config를
 * 끄고, 활성화 주체를 이 클래스 하나로 만들었다. Moments의 시간 이벤트는 이 앱에 리스너가 없어 잃는 기능이 없다. 대신 이 클래스가 빠지면 스케줄러가 조용히
 * 멈추므로, 그 계약은 {@code SchedulingConfigIntegrationTest}가 가드한다.
 *
 * <p>기본 {@code TaskScheduler}는 풀 크기가 1이라 스케줄러들이 한 스레드에서 직렬 실행된다. 현재는 1초 주기 push 폴링 하나뿐이라 문제가 없지만,
 * 오래 걸리는 스케줄러가 추가되면 {@code spring.task.scheduling.pool.size}를 함께 재검토한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
