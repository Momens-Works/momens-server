package works.momens.server.project.task;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import works.momens.server.project.core.ProjectReader;

/**
 * task 조회 public API.
 *
 * <p>모바일 보드(MOM-62)와 상세(MOM-63)가 project 내부 repository를 직접 참조하지 않고 태스크를 읽도록 합니다.
 */
public interface TaskReader {

  /**
   * 주어진 상태에 해당하는 태스크를 조회합니다. 어떤 상태를 보일지는 호출하는 표면이 정합니다(모바일 보드는 저장 상태 5종을 모두 넘깁니다. MOM-75). 소프트 삭제된
   * 태스크는 제외하며, 정렬은 생성 시각 내림차순이고 같으면 id 내림차순입니다.
   */
  List<BoardTask> listTasksByStatus(UUID projectId, Collection<String> statuses);

  /**
   * 태스크 한 건의 상세를 조회합니다. 소프트 삭제된 태스크는 빈 값으로 취급합니다. 없을 때 어떤 에러로 응답할지는 호출하는 쪽이 정합니다({@link
   * ProjectReader}와 같은 계약 스타일).
   */
  Optional<TaskDetail> findDetail(UUID taskId);

  /**
   * 태스크가 속한 workspace id를 조회합니다. 수정 화면이 태스크를 바꾸기 전에 멤버십을 확인하는 용도라, 상세 전체를 읽지 않고 workspace만 가져옵니다.
   * 소프트 삭제된 태스크는 빈 값으로 취급합니다.
   */
  Optional<UUID> workspaceIdOf(UUID taskId);

  /**
   * 태스크가 속한 workspace와 project를 조회합니다. 다른 하위 도메인이 태스크 내부 저장소를 참조하지 않고 소속만 확인할 때 사용합니다. 소프트 삭제된 태스크는
   * 빈 값으로 취급합니다.
   */
  Optional<TaskScope> findScope(UUID taskId);

  /** 웹 레거시 응답 폭의 태스크 한 건을 조회합니다. */
  Optional<WebTaskDetail> findWebDetail(UUID taskId);

  /** 프로젝트에 속한 웹 레거시 응답 폭의 태스크를 생성 시각 내림차순으로 조회합니다. */
  List<WebTaskDetail> listWebDetailsByProjectId(UUID projectId);

  /** 워크스페이스에 속한 웹 레거시 응답 폭의 태스크를 생성 시각 내림차순으로 조회합니다. */
  List<WebTaskDetail> listWebDetailsByWorkspaceId(UUID workspaceId);
}
