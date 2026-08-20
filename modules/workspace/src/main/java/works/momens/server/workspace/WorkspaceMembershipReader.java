package works.momens.server.workspace;

import java.util.List;
import java.util.UUID;

/** workspace 멤버십 조회 public API. */
public interface WorkspaceMembershipReader {

  /** 워크스페이스의 모든 멤버십을 조회합니다. 결과를 정렬하지 않으므로 순서가 필요한 소비자가 정렬 기준을 적용합니다. */
  List<WorkspaceMembershipDetail> listDetailsByWorkspaceId(UUID workspaceId);
}
