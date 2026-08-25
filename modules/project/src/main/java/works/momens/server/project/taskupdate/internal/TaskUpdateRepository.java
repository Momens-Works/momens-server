package works.momens.server.project.taskupdate.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaskUpdateRepository extends JpaRepository<TaskUpdate, UUID> {

  List<TaskUpdate> findByTaskIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(UUID taskId);

  Optional<TaskUpdate> findByIdAndTaskIdAndDeletedAtIsNull(UUID id, UUID taskId);
}
