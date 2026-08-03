package works.momens.server.minsu.internal.ledger;

import java.time.Instant;
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
}
