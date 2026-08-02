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
 * <p>사실 스케줄링은 지금도 항상 켜져 있다. spring-modulith-starter-core의 {@code MomentsAutoConfiguration}이 클래스 레벨
 * {@code @EnableScheduling}을 달고 있어 조건 없이 활성화된다. 그래서 push를 꺼도 다른 모듈의 스케줄러는 돌았다. 다만 그 보장이 무관한 라이브러리의
 * 부수효과에 얹혀 있어, 의존성이 빠지거나 조건이 붙으면 소리 없이 사라진다. 활성화를 여기서 명시적으로 소유해 그 우연을 없앤다.
 *
 * <p>기본 {@code TaskScheduler}는 풀 크기가 1이라 스케줄러들이 한 스레드에서 직렬 실행된다. 현재는 1초 주기 push 폴링 하나뿐이라 문제가 없지만,
 * 오래 걸리는 스케줄러가 추가되면 {@code spring.task.scheduling.pool.size}를 함께 재검토한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
