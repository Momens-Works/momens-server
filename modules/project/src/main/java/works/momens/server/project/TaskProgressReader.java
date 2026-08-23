package works.momens.server.project;

import java.util.OptionalInt;
import java.util.UUID;

/** task 상태를 기준으로 프로젝트 진행률을 계산하는 공개 조회 계약. */
public interface TaskProgressReader {

  /**
   * cancelled를 제외한 task 중 done 비율을 0~100 정수로 반환합니다.
   *
   * <p>프로젝트가 없으면 빈 값을, task가 없으면 0을 반환합니다.
   */
  OptionalInt progressOf(UUID projectId);
}
