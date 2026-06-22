package works.momens.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 애플리케이션 컨텍스트 로드 테스트.
 *
 * <p>실제 PostgreSQL(pgvector) 컨테이너를 띄워 datasource, JPA, Flyway 자동 구성을 포함한 전체 컨텍스트가 정상적으로 로드되는지
 * 검증합니다. repository/migration 호환성 검증에 H2를 사용하지 않습니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MomensServerApplicationTests {

  @Container
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Test
  void contextLoads() {}
}
