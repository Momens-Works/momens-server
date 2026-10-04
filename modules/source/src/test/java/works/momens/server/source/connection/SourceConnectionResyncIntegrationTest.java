package works.momens.server.source.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SourceConnectionResyncIntegrationTest extends AbstractPostgresIntegrationTest {
  @Autowired private SourceConnectionRepository repository;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("오래된 요청이 늦게 저장되어도 요청 시각과 수정 시각이 역행하지 않는다")
  void delayedOlderRequestCannotMoveRequestOrUpdateTimeBackwards() {
    UUID workspaceId = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO workspaces (id, name, slug) VALUES (?, 'resync', ?)",
        workspaceId,
        workspaceId.toString());
    jdbc.update(
        "INSERT INTO source_connections (id, workspace_id, source_type, status, updated_at)"
            + " VALUES (?, ?, 'GITHUB', 'ACTIVE', '2026-01-01T00:00:00Z')",
        id,
        workspaceId);
    Instant older = Instant.parse("2026-09-01T00:00:00Z");
    Instant newer = older.plusSeconds(10);
    assertThat(repository.requestResync(id, workspaceId, newer)).isEqualTo(1);
    assertThat(repository.requestResync(id, workspaceId, older)).isEqualTo(1);
    assertThat(time(id, "resync_requested_at")).isEqualTo(newer);
    assertThat(time(id, "updated_at")).isEqualTo(newer);

    Instant workerUpdate = newer.plusSeconds(10);
    jdbc.update(
        "UPDATE source_connections SET updated_at = ? WHERE id = ?",
        Timestamp.from(workerUpdate),
        id);
    repository.requestResync(id, workspaceId, newer.plusSeconds(1));
    assertThat(time(id, "resync_requested_at")).isEqualTo(newer.plusSeconds(1));
    assertThat(time(id, "updated_at")).isEqualTo(workerUpdate);
  }

  private Instant time(UUID id, String column) {
    return jdbc.queryForObject(
            "SELECT " + column + " FROM source_connections WHERE id = ?", Timestamp.class, id)
        .toInstant();
  }
}
