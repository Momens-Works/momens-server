package works.momens.server.project.milestone.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.core.ProjectOwnerReader;
import works.momens.server.project.milestone.CreateMilestoneCommand;
import works.momens.server.project.milestone.MilestoneCreator;
import works.momens.server.project.milestone.MilestoneDetail;

/**
 * 레거시 마일스톤 생성 서비스의 검증과 저장 동작을 이관한 구현입니다.
 *
 * <p>검증 순서는 레거시와 동일하게 마일스톤 이름, 소유자, {@code health_status}, 진행률 순입니다.
 *
 * <p>프로젝트 생성과 달리 라벨과 집계 컬럼은 다루지 않습니다. {@code milestones} 테이블에는 해당 컬럼이 없습니다. 상태는 요청으로 받지 않고 항상
 * {@code planned}로 저장합니다. 레거시의 마일스톤 생성 요청도 상태를 입력받지 않습니다.
 */
@Service
@RequiredArgsConstructor
class MilestoneCreatorImpl implements MilestoneCreator {

  private static final String FIELD_NAME = "name";
  private static final String FIELD_HEALTH_STATUS = "health_status";
  private static final String FIELD_PROGRESS = "progress";

  private final MilestoneRepository milestoneRepository;
  private final MilestoneOwnerRepository milestoneOwnerRepository;
  private final ProjectOwnerReader projectOwnerReader;
  private final MilestoneOwnerMembershipChecker ownerMembershipChecker;

  @Override
  @Transactional
  public MilestoneDetail create(CreateMilestoneCommand command) {
    if (command.name() == null || command.name().isEmpty()) {
      throw validation(FIELD_NAME);
    }
    List<UUID> ownerUserIds = resolveOwnerUserIds(command);
    ownerMembershipChecker.requireWorkspaceMembers(command.workspaceId(), ownerUserIds);

    // 소유자 행이 milestones를 참조하므로 마일스톤 INSERT를 먼저 실행합니다.
    Milestone milestone =
        milestoneRepository.saveAndFlush(
            Milestone.builder()
                .projectId(command.projectId())
                .name(command.name())
                .description(emptyToNull(command.description()))
                .targetDate(command.targetDate())
                .healthStatus(healthStatusOf(command.healthStatus()))
                .progress(progressOf(command.progress()))
                .summary(emptyToNull(command.summary()))
                .lastContextAt(command.lastContextAt())
                .build());
    milestoneOwnerRepository.saveAll(
        ownerUserIds.stream()
            .map(ownerId -> MilestoneOwner.of(milestone.getId(), ownerId))
            .toList());

    return toDetail(milestone, ownerUserIds);
  }

  /**
   * 마일스톤에 저장할 소유자 목록을 결정합니다.
   *
   * <p>요청에서 소유자를 지정하면 전달받은 목록을 그대로 사용합니다. 소유자를 지정하지 않으면 프로젝트 소유자를 사용하고, 프로젝트 소유자도 없으면 요청자 한 명을 소유자로
   * 지정합니다. 소유자 목록이 비어 있을 때 바로 요청자를 사용하는 프로젝트 생성과는 기본값 결정 순서가 다릅니다.
   *
   * <p>프로젝트 소유자는 레거시와 동일하게 {@code created_at}, {@code owner_user_id} 순으로 조회합니다. 이 정렬 순서는 응답의 {@code
   * owner_user_ids}에도 그대로 반영됩니다.
   */
  private List<UUID> resolveOwnerUserIds(CreateMilestoneCommand command) {
    List<UUID> requested = command.ownerUserIds();
    if (requested != null && !requested.isEmpty()) {
      return List.copyOf(requested);
    }
    List<UUID> projectOwnerUserIds = projectOwnerReader.listOwnerUserIds(command.projectId());
    return projectOwnerUserIds.isEmpty() ? List.of(command.requesterId()) : projectOwnerUserIds;
  }

  private static String healthStatusOf(String requested) {
    if (requested == null || requested.isEmpty()) {
      return HealthStatus.PLANNED.value();
    }
    return HealthStatus.from(requested).orElseThrow(() -> validation(FIELD_HEALTH_STATUS)).value();
  }

  private static int progressOf(Integer requested) {
    if (requested == null) {
      return 0;
    }
    if (requested < 0 || requested > 100) {
      throw validation(FIELD_PROGRESS);
    }
    return requested;
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private static BusinessException validation(String field) {
    return new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED, Map.of("field", field));
  }

  private static MilestoneDetail toDetail(Milestone milestone, List<UUID> ownerUserIds) {
    return new MilestoneDetail(
        milestone.getId(),
        milestone.getProjectId(),
        milestone.getName(),
        milestone.getDescription(),
        milestone.getTargetDate(),
        milestone.getStatus(),
        ownerUserIds,
        milestone.getHealthStatus(),
        milestone.getProgress(),
        milestone.getSummary(),
        milestone.getLastContextAt(),
        milestone.getCreatedAt(),
        milestone.getUpdatedAt());
  }
}
