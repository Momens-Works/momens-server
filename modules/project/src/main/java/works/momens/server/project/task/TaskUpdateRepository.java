package works.momens.server.project.task;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaskUpdateRepository extends JpaRepository<TaskUpdate, UUID> {

  List<TaskUpdate> findByTaskIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(UUID taskId);
}
