package works.momens.server.project.milestone;

import java.util.UUID;

/** 다른 project 하위 도메인이 milestone의 소속과 생존 여부를 확인하는 공개 계약. */
public interface MilestoneDirectory {

  /** 소프트 삭제되지 않은 milestone이 지정한 project에 속하는지 확인합니다. */
  boolean existsInProject(UUID milestoneId, UUID projectId);
}
