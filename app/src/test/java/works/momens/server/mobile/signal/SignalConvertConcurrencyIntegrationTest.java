package works.momens.server.mobile.signal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.signal.SignalActionResult;
import works.momens.server.signal.SignalActionService;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/**
 * 같은 Signal에 대한 동시 convert-to-task가 실제 PostgreSQL에서도 task 한 건만 남기는지 검증한다(MOM-0804 완료 조건).
 *
 * <p>{@code SignalActionServiceImpl} 단위 테스트는 mock executor가 던지는 {@code
 * DataIntegrityViolationException}으로 복구 분기만 확인한다. 이 테스트는 {@code signal_actions}의 {@code
 * UNIQUE(signal_id)}가 실제로 위반되는 상황에서 패배한 요청의 task·outbox insert가 함께 rollback되고 승리한 요청의 결과로
 * replay되는지를 본다. HTTP status 매핑({@code created} → 201/200)은 {@link MobileSignalsIntegrationTest}가
 * 담당하므로 여기서는 스레드 안전성이 보장되는 서비스 public API를 직접 호출한다.
 *
 * <p>두 스레드가 모두 ledger 없음을 확인하는 데 성공하면 한쪽이 unique 위반으로 복구하고, 한쪽이 먼저 커밋을 마치면 다른 쪽은 기존 ledger를 보고
 * replay한다. 어느 순서든 결과 계약은 같으므로 실행 타이밍에 의존하지 않는다.
 */
@SpringBootTest
class SignalConvertConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private SignalActionService signalActionService;
  @Autowired private UserService userService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("동시 convert 요청은 task·ledger·outbox를 한 벌만 남기고 나머지는 replay로 되돌린다")
  void concurrentConvertsCreateExactlyOneTask() throws Exception {
    UserProfile jinsu = userService.findOrCreate("signals-it-race@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("signals-race");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "signals-race-project");
    UUID signal = insertSignal(workspace, project, "risk", "이탈 가능성 발견");

    CyclicBarrier barrier = new CyclicBarrier(2);
    Callable<SignalActionResult> convert =
        () -> {
          barrier.await(10, TimeUnit.SECONDS);
          return signalActionService.convertToTask(signal, jinsu.id());
        };
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<SignalActionResult> results;
    try {
      List<Future<SignalActionResult>> futures = pool.invokeAll(List.of(convert, convert));
      results = List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get());
    } finally {
      pool.shutdownNow();
    }

    assertThat(results).filteredOn(SignalActionResult::created).hasSize(1);
    assertThat(results).filteredOn(result -> !result.created()).hasSize(1);
    assertThat(results).extracting(result -> result.task().id()).containsOnly(taskId(project));

    assertThat(countTasks(project)).isEqualTo(1);
    assertThat(countActions(signal)).isEqualTo(1);
    assertThat(countEvents(workspace, "signal.converted_to_task")).isEqualTo(1);
    assertThat(countEvents(workspace, "task.created")).isEqualTo(1);
  }

  private UUID taskId(UUID projectId) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM tasks WHERE project_id = ?", UUID.class, projectId);
  }

  private Integer countTasks(UUID projectId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tasks WHERE project_id = ?", Integer.class, projectId);
  }

  private Integer countActions(UUID signalId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM signal_actions WHERE signal_id = ?", Integer.class, signalId);
  }

  private Integer countEvents(UUID workspaceId, String eventType) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM outbox_events WHERE workspace_id = ? AND event_type = ?",
        Integer.class,
        workspaceId,
        eventType);
  }

  private UUID insertWorkspace(String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO workspaces (id, name, slug) VALUES (?, ?, ?)", id, "모멘스", slug);
    return id;
  }

  private void addMember(UUID workspaceId, UUID userId, String role) {
    jdbcTemplate.update(
        "INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, ?)",
        workspaceId,
        userId,
        role);
  }

  private UUID insertProject(UUID workspaceId, UUID ownerId, String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO projects (id, workspace_id, name, owner_id) VALUES (?, ?, ?, ?)",
        id,
        workspaceId,
        name,
        ownerId);
    return id;
  }

  private UUID insertSignal(UUID workspaceId, UUID projectId, String type, String title) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO signals (id, workspace_id, project_id, type, title, description, occurred_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, now())",
        id,
        workspaceId,
        projectId,
        type,
        title,
        "권한 요청 타이밍이 늦어 이탈 가능성이 있습니다.");
    return id;
  }
}
