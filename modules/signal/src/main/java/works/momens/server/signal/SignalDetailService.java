package works.momens.server.signal;

import java.util.UUID;

/** Signal 상세 조회 공개 API. */
public interface SignalDetailService {

  SignalDetail getDetail(UUID signalId, UUID userId);
}
