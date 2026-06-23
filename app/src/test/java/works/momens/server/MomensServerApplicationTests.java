package works.momens.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

/**
 * 애플리케이션 컨텍스트 로드 테스트.
 *
 * <p>실제 PostgreSQL(pgvector) 컨테이너 위에서 datasource, JPA, Flyway(모듈별 마이그레이션 병합) 자동 구성을 포함한 전체 컨텍스트가 정상
 * 로드되는지 검증합니다. 컨테이너 설정은 {@link AbstractPostgresIntegrationTest}가 제공합니다.
 */
@SpringBootTest
class MomensServerApplicationTests extends AbstractPostgresIntegrationTest {

  @Test
  void contextLoads() {}
}
