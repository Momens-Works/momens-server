package works.momens.server.signal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** action 모듈이 Signal 원본을 읽을 때 쓰는 공개 API. 소프트 삭제된 Signal은 없는 것으로 취급한다. */
public interface SignalReader {

  Optional<Snapshot> findLive(UUID signalId);

  /**
   * 민수 draft 생성 컨텍스트용 근거(대상·변화·영향)를 표시 순서대로 읽는다(MOM-0692). action 경로에서 evidence를 조립할 때 쓰며, source
   * hydrate 없이 signal_evidence의 의미 값만 반환한다.
   */
  List<EvidenceRow> readEvidence(UUID signalId);

  /** action 처리에 필요한 Signal 최소 스냅샷. */
  record Snapshot(UUID id, UUID workspaceId, UUID projectId, String title) {}

  /** 민수 컨텍스트용 근거 의미 값. */
  record EvidenceRow(String target, String change, String impact) {}
}
