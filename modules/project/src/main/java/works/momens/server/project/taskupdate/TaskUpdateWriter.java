package works.momens.server.project.taskupdate;

import java.util.Map;
import java.util.UUID;

/** 웹 Product API의 task update write public API입니다. */
public interface TaskUpdateWriter {

  TaskUpdateDetail create(
      UUID taskId, UUID userId, String body, String kind, Map<String, Object> metadata);

  void delete(UUID taskId, UUID updateId, UUID userId);
}
