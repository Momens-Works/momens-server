package works.momens.server.workspace;

import java.util.List;
import java.util.UUID;

/** workspace 멤버십 조회 public API. */
public interface WorkspaceMembershipReader {

  /**
   * 워크스페이스의 모든 멤버십을 가입 시각 오름차순으로 조회합니다. 가입 시각이 같으면 사용자 식별자 오름차순으로 정렬합니다.
   *
   * <p>웹 snapshot 계약 문서 4.4절에서 확정한 {@code members} 정렬 기준입니다. SQL에서 정렬을 처리하므로 호출하는 쪽에서는 전달받은 순서를
   * 유지해야 합니다.
   */
  List<WorkspaceMembershipDetail> listDetailsByWorkspaceId(UUID workspaceId);
}
