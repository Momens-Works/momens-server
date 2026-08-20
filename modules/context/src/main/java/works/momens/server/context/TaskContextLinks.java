package works.momens.server.context;

import java.util.List;
import java.util.UUID;

/** 태스크 context bundle 조합에 필요한 live relation id 집합입니다. */
public record TaskContextLinks(List<UUID> memoryIds, List<UUID> sourceRefIds) {
  public TaskContextLinks {
    memoryIds = memoryIds == null ? List.of() : memoryIds;
    sourceRefIds = sourceRefIds == null ? List.of() : sourceRefIds;
  }
}
