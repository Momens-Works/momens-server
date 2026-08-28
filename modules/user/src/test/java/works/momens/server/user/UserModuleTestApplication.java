package works.momens.server.user;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 슬라이스 테스트용 부트스트랩.
 *
 * <p>feature 모듈에는 실행 가능한 {@code @SpringBootApplication}이 없으므로, 슬라이스 테스트가 위로 탐색해 찾을
 * {@code @SpringBootConfiguration}을 테스트 스코프에 둡니다. app 클래스패스에는 노출되지 않습니다(테스트 소스).
 *
 * <p>{@code @ComponentScan}을 둡니다: 통합테스트가 서비스 빈을 찾아야 하고, 슬라이스의 TypeExcludeFilter가 관련 없는 빈을 걸러내므로
 * {@code @DataJpaTest}에도 안전합니다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
class UserModuleTestApplication {}
