package works.momens.server.workspace.access;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.workspace.WorkspaceMembershipDetail;
import works.momens.server.workspace.WorkspaceMembershipReader;

/** 멤버십 엔티티를 조회해 public API의 결과 타입으로 변환하는 구현입니다. */
@Service
@RequiredArgsConstructor
class WorkspaceMembershipReaderImpl implements WorkspaceMembershipReader {

  private final WorkspaceMemberRepository workspaceMemberRepository;

  @Override
  @Transactional(readOnly = true)
  public List<WorkspaceMembershipDetail> listDetailsByWorkspaceId(UUID workspaceId) {
    return workspaceMemberRepository
        .findByWorkspaceIdOrderByCreatedAtAscUserIdAsc(workspaceId)
        .stream()
        .map(
            member ->
                new WorkspaceMembershipDetail(
                    member.getUserId(),
                    member.getRole(),
                    member.getCreatedAt(),
                    member.getUpdatedAt()))
        .toList();
  }
}
