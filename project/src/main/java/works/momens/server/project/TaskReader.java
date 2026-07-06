package works.momens.server.project;

import java.util.List;
import java.util.UUID;

/**
 * task 조회 public API.
 *
 * <p>모바일 보드(MOM-62)가 project 내부 repository를 직접 참조하지 않고 태스크를 읽도록 합니다.
 */
public interface TaskReader {

  /**
   * 보드에 노출할 태스크를 조회합니다. 보드가 다루는 상태(todo, in_progress, done)만 담고, 레거시의 backlog와 cancelled는 제외합니다.
   * 소프트 삭제된 태스크는 제외하며, 정렬은 생성 시각 내림차순(같으면 id 내림차순)입니다.
   */
  List<BoardTask> listBoardTasks(UUID projectId);
}
