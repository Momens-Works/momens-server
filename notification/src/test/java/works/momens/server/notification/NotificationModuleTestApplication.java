package works.momens.server.notification;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 슬라이스 테스트용 부트스트랩.
 *
 * <p>feature 모듈에는 실행 가능한 {@code @SpringBootApplication}이 없으므로, 슬라이스 테스트가 위로 탐색해 찾을
 * {@code @SpringBootConfiguration}을 테스트 스코프에 둡니다. {@code @ComponentScan}에 {@code
 * TypeExcludeFilter}를 excludeFilters로 명시해, {@code @DataJpaTest} 슬라이스에 모듈의 모든 {@code @Service}가 자동으로
 * 올라오지 않게 합니다(signal 부트스트랩과 동일).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    excludeFilters =
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
class NotificationModuleTestApplication {}
