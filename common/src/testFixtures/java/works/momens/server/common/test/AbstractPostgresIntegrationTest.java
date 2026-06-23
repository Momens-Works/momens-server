package works.momens.server.common.test;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL 통합테스트 베이스.
 *
 * <p>repository 등 DB 통합테스트가 상속합니다. H2가 아니라 실제 PostgreSQL(pgvector) Testcontainers를 사용합니다([데이터]
 * docs/rules/persistence.md). 컨테이너는 singleton으로 한 번만 기동해 테스트 클래스 간 재사용하며, 종료는 Testcontainers(Ryuk)가
 * 담당합니다.
 */
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
