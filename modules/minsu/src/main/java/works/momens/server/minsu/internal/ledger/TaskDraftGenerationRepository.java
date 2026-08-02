package works.momens.server.minsu.internal.ledger;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaskDraftGenerationRepository extends JpaRepository<TaskDraftGeneration, UUID> {

  Optional<TaskDraftGeneration> findByTaskId(UUID taskId);
}
