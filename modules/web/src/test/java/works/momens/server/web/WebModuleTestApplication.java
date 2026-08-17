package works.momens.server.web;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 슬라이스 테스트용 부트스트랩.
 *
 * <p>feature 모듈에는 실행 가능한 {@code @SpringBootApplication}이 없으므로, 슬라이스 테스트가 위로 탐색해 찾을
 * {@code @SpringBootConfiguration}을 테스트 스코프에 둡니다. app 클래스패스에는 노출되지 않습니다(테스트 소스).
 *
 * <p>{@code @ComponentScan}을 둡니다: {@code @WebMvcTest}가 컨트롤러를 component scan으로 발견하기 때문입니다. {@code
 * TypeExcludeFilter}를 excludeFilters로 명시해야 슬라이스가 관련 없는 빈을 걸러냅니다(mobile 모듈 전례).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    excludeFilters =
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
class WebModuleTestApplication {}
