package works.momens.server.source;

import java.util.List;
import java.util.UUID;

/**
 * source 연결 조회 public API입니다.
 *
 * <p>{@code source_connections}는 레거시 {@code momens-api}가 소유하는 외부 테이블이며, 신규 서버는 연결 승인 흐름에서만 쓰기 작업을
 * 수행합니다. 조회는 워크스페이스 단위로만 제공하며, 권한은 호출하는 쪽에서 확인합니다.
 */
public interface SourceConnectionReader {

  /**
   * 워크스페이스에 속한 source 연결을 생성 시각 내림차순으로 조회합니다.
   *
   * <p>정렬 기준은 레거시의 연결 목록 조회 쿼리와 같습니다.
   */
  List<SourceConnectionDetail> listDetailsByWorkspaceId(UUID workspaceId);
}
