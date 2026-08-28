package works.momens.server.memory;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 슬라이스 테스트용 부트스트랩입니다.
 *
 * <p>feature 모듈에는 실행 가능한 {@code @SpringBootApplication}이 없으므로, 슬라이스 테스트가 탐색할
 * {@code @SpringBootConfiguration}을 테스트 스코프에 제공합니다.
 *
 * <p>{@code @ComponentScan}에는 {@code TypeExcludeFilter}를 {@code excludeFilters}로 등록해
 * {@code @DataJpaTest}에서 모듈의 {@code @Service}가 자동으로 등록되지 않도록 합니다. 구성은 context·source 모듈의 테스트 부트스트랩과
 * 동일합니다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    excludeFilters =
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
class MemoryModuleTestApplication {}
