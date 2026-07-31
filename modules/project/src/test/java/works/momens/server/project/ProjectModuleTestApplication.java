package works.momens.server.project;

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
 * <p>{@code @ComponentScan}에 {@code TypeExcludeFilter}를 excludeFilters로 명시합니다.
 * {@code @SpringBootApplication}은 이 필터를 자체 선언하지만 일반 {@code @ComponentScan}에는 없어서, 명시하지 않으면
 * {@code @DataJpaTest} 슬라이스에 모듈의 모든 {@code @Service}가 올라옵니다. task 생성 서비스처럼 다른 모듈 public API(라벨 발급)를
 * 의존하는 빈이 슬라이스에 끌려오면 그 의존을 못 찾아 컨텍스트가 깨집니다. 필터를 두면 슬라이스는 JPA 빈과 명시적으로 {@code @Import}한 서비스만
 * 올립니다(mobile 부트스트랩과 동일, MOM-61).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    excludeFilters =
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
class ProjectModuleTestApplication {}
