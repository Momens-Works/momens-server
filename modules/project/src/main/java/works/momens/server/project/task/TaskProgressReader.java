package works.momens.server.project.task;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * task 상태를 기준으로 프로젝트 진행률을 계산하는 공개 조회 계약.
 *
 * <p>진행률의 분모와 분자가 모두 task라서 계산 책임을 project core가 아닌 task 경계가 소유합니다(MOM-0887).
 */
public interface TaskProgressReader {

  /**
   * project 진행률을 0~100 사이의 정수 퍼센트로 조회합니다.
   *
   * <p>진행률은 저장된 {@code projects.progress}를 사용하지 않고 태스크 상태에서 계산합니다. cancelled를 제외한 태스크를 기준으로 done
   * 비율을 계산하며, 소수점은 버립니다. 소프트 삭제된 태스크는 제외하고, 태스크가 없으면 0을 반환합니다. 프로젝트가 없으면 빈 값을 반환하고, 어떤 에러로 응답할지는
   * 호출하는 쪽이 결정합니다.
   *
   * <p>계산 기준은 2026-06-24 기획 회의 ADR을 따르며, cancelled 제외도 기획이 확정했습니다. 소수점 버림만 아직 확정되지 않아 서버 구현에서
   * 결정했고(ADR-0013), 추후 기획 확정 시 변경될 수 있습니다.
   */
  OptionalInt progressOf(UUID projectId);
}
