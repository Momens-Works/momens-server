package works.momens.server.signal;

import java.util.List;
import java.util.UUID;

/** 프로젝트 스코프의 미처리 Signal 목록 조회 공개 API. */
public interface SignalListService {

  List<SignalSummary> listUnprocessed(UUID projectId, UUID userId);
}
