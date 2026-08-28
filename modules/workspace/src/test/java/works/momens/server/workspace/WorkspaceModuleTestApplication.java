package works.momens.server.workspace;

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
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    excludeFilters =
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
class WorkspaceModuleTestApplication {}
