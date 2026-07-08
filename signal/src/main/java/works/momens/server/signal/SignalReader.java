package works.momens.server.signal;

import java.util.Optional;
import java.util.UUID;

/** action 모듈이 Signal 원본을 읽을 때 쓰는 공개 API. 소프트 삭제된 Signal은 없는 것으로 취급한다. */
public interface SignalReader {

  Optional<Snapshot> findLive(UUID signalId);

  /** action 처리에 필요한 Signal 최소 스냅샷. */
  record Snapshot(UUID id, UUID workspaceId, UUID projectId, String title) {}
}
