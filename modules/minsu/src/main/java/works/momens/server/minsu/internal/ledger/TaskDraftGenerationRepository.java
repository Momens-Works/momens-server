package works.momens.server.minsu.internal.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TaskDraftGenerationRepository extends JpaRepository<TaskDraftGeneration, UUID> {

  Optional<TaskDraftGeneration> findByTaskId(UUID taskId);

  /** 적재 시점의 두 deadline은 원장과 같은 시계로 계산한다(8.6절). 읽기 투영도 같은 {@code NOW()}를 쓴다. */
  @Query(value = "SELECT NOW()", nativeQuery = true)
  Instant currentDatabaseTime();

  /**
   * 아직 생성이 진행 중인 원장이 있는지 판정한다(8.6절의 읽기 투영).
   *
   * <p>나이 상한을 DB에서 비교 하나로 끝내므로 scheduler가 멈춰 있어도, 설정을 꺼서 rollback해도 앱이 {@code generating}에 무기한 갇히지
   * 않는다. 상한을 scheduler가 강제하게 두면 scheduler가 멈춘 상황을 막으려는 장치를 그 scheduler가 실행하는 순환이 된다.
   */
  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM minsu_task_draft_generations "
              + "WHERE task_id = :taskId AND status <> 'completed' AND read_deadline_at > NOW())",
      nativeQuery = true)
  boolean existsGenerating(@Param("taskId") UUID taskId);

  /**
   * 재시도 시각이 지난 {@code pending} 원장을 claim 대상으로 잠근다(8.5절).
   *
   * <p>{@code FOR UPDATE SKIP LOCKED}로 여러 인스턴스가 같은 행을 집지 않게 한다({@code push_deliveries}와 같은 방식).
   * {@code (next_attempt_at) WHERE status = 'pending'} 부분 인덱스가 그대로 적중한다.
   *
   * <p>{@code apply_cutoff_at}이 지난 행은 제외한다. 반영 창이 이미 닫혀 결과를 쓸 수 없으므로 claim해도 provider 호출만 낭비된다. 그런
   * 행의 물리적 정리는 나중에 해도 되고, public 계약은 읽기 투영이 이미 닫았다(8.6절).
   */
  @Query(
      value =
          "SELECT * FROM minsu_task_draft_generations "
              + "WHERE status = 'pending' AND next_attempt_at <= NOW() AND apply_cutoff_at > NOW() "
              + "ORDER BY next_attempt_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<TaskDraftGeneration> lockDuePending(@Param("limit") int limit);

  /**
   * lease가 만료된 {@code processing} 원장을 회수 대상으로 잠근다(8.5절).
   *
   * <p>claim을 보유한 채 프로세스가 종료되면 이 경로로 돌아온다. 만료 판정은 DB 시계 기준이라 앱 서버 간 시계 차이의 영향을 받지 않는다. {@code
   * (lease_expires_at) WHERE status = 'processing'} 부분 인덱스가 적중한다.
   */
  @Query(
      value =
          "SELECT * FROM minsu_task_draft_generations "
              + "WHERE status = 'processing' AND lease_expires_at <= NOW() "
              + "AND apply_cutoff_at > NOW() "
              + "ORDER BY lease_expires_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<TaskDraftGeneration> lockExpiredProcessing(@Param("limit") int limit);

  /** 결과 기록 트랜잭션이 대상 행을 잠근다. 여기서 잠가야 claim token 재검증과 전이가 원자적이다(8.2절). */
  @Query(
      value = "SELECT * FROM minsu_task_draft_generations WHERE id = :id FOR UPDATE",
      nativeQuery = true)
  Optional<TaskDraftGeneration> lockById(@Param("id") UUID id);
}
