package works.momens.server.project;

/**
 * {@code project} 모듈의 마일스톤 생성 public API입니다.
 *
 * <p>레거시 {@code POST /projects/:projectId/milestones}의 저장 동작을 그대로 이관합니다. 마일스톤과 {@code
 * milestone_owners}는 하나의 트랜잭션으로 함께 저장합니다.
 *
 * <p>{@link ProjectCreator}와 마찬가지로 요청자의 권한은 확인하지 않습니다. {@code milestones} 테이블에는 워크스페이스 식별자가 없어
 * 프로젝트를 통해 소속 워크스페이스를 확인해야 합니다. 이 조회는 권한을 확인하는 호출자가 이미 수행하므로 이 API에서는 반복하지 않습니다.
 */
public interface MilestoneCreator {

  /** 마일스톤을 저장하고 웹 응답에 필요한 필드를 반환합니다. */
  MilestoneDetail create(CreateMilestoneCommand command);
}
