package works.momens.server.source;

import java.util.UUID;

/**
 * source-ref를 확인한 것으로 표시하는 public API입니다.
 *
 * <p>검증한 사용자와 시각을 기록하고 갱신된 source-ref의 전체 필드를 반환합니다. 권한은 호출하는 쪽에서 확인합니다.
 */
public interface SourceRefVerifier {

  SourceRefDetail verify(UUID sourceRefId, UUID userId);
}
