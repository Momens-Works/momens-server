package works.momens.server.project.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.MilestoneDetail;
import works.momens.server.project.MilestoneReader;

@Service
@RequiredArgsConstructor
class MilestoneReaderImpl implements MilestoneReader {

  private final MilestoneRepository milestoneRepository;
  private final MilestoneOwnerRepository milestoneOwnerRepository;

  @Override
  @Transactional(readOnly = true)
  public List<MilestoneDetail> listDetailsByWorkspaceId(UUID workspaceId) {
    return withOwners(milestoneRepository.findDetailRowsByWorkspaceId(workspaceId));
  }

  /**
   * 행 목록에 소유자를 채웁니다. {@code milestone_owners} 조회는 마일스톤 수와 무관하게 한 번입니다.
   *
   * <p>소유자 행이 없으면 빈 목록입니다. project와 달리 폴백할 소유자 컬럼이 레거시 스키마에 없어, 레거시도 빈 배열을 그대로 돌려줍니다(웹 snapshot 계약
   * 2.3).
   */
  private List<MilestoneDetail> withOwners(List<MilestoneDetailRow> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    List<UUID> milestoneIds = rows.stream().map(MilestoneDetailRow::id).toList();
    Map<UUID, List<UUID>> ownersByMilestoneId =
        milestoneOwnerRepository
            .findByMilestoneIdInOrderByCreatedAtAscOwnerUserIdAsc(milestoneIds)
            .stream()
            .collect(
                Collectors.groupingBy(
                    MilestoneOwner::getMilestoneId,
                    // groupingBy는 스트림 순서를 유지하므로 정렬은 조회에서 정해진 그대로다.
                    Collectors.mapping(MilestoneOwner::getOwnerUserId, Collectors.toList())));
    return rows.stream()
        .map(row -> row.toDetail(ownersByMilestoneId.getOrDefault(row.id(), List.of())))
        .toList();
  }
}
