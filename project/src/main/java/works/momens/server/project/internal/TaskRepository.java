package works.momens.server.project.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaskRepository extends JpaRepository<Task, UUID> {

  /**
   * 보드용 조회. 주어진 상태의 소프트 삭제되지 않은 태스크를 생성 시각 내림차순으로, 같은 시각은 id 내림차순으로 정렬합니다. id 보조 정렬은 같은 마이크로초 생성 행의
   * 순서를 고정하기 위한 것입니다(pagination-tiebreaker 규칙).
   */
  List<Task> findByProjectIdAndStatusInAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
      UUID projectId, Collection<String> statuses);
}
