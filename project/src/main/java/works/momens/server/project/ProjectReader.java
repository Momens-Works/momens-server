package works.momens.server.project;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * project 모듈의 조회 public API.
 *
 * <p>모바일 API(MOM-60, MOM-61)와 다른 모듈이 project 내부 repository를 직접 참조하지 않고도 project 정보를 읽을 수 있도록 합니다.
 * 소프트 삭제된 project는 모든 메서드에서 없는 것으로 취급합니다. project가 없을 때 반환할 에러(PROJECT_NOT_FOUND 등)는 호출하는 쪽에서
 * 결정합니다.
 */
public interface ProjectReader {

  /** projectId가 속한 workspace id를 조회합니다. project 리소스 접근 권한 검사의 앞 단계에 사용합니다. */
  Optional<UUID> workspaceIdOf(UUID projectId);

  /** project 한 건의 스냅샷을 조회합니다. */
  Optional<ProjectSnapshot> findSnapshot(UUID projectId);

  /**
   * project 진행률을 0~100 사이의 정수 퍼센트로 조회합니다.
   *
   * <p>진행률은 저장된 {@code projects.progress}를 사용하지 않고 태스크 상태에서 계산합니다. cancelled를 제외한 태스크를 기준으로 done
   * 비율을 계산하며, 소수점은 버립니다. 소프트 삭제된 태스크는 제외하고, 태스크가 없으면 0을 반환합니다.
   *
   * <p>계산 기준은 2026-06-24 기획 회의 ADR을 따릅니다. cancelled 제외와 소수점 버림은 ADR에 명시되지 않아 서버 구현에서
   * 결정했으며(ADR-0013), 추후 기획 확정 시 변경될 수 있습니다.
   */
  OptionalInt progressOf(UUID projectId);

  /**
   * workspaceIds에 속한 project를 모두 조회합니다. 정렬은 레거시와 같은 생성 시각 내림차순입니다.
   *
   * <p>사용자의 접근 범위(workspace 스코프)는 호출하는 쪽이 멤버십을 한 번 조회해서 확정해 넘깁니다. 이 모듈이 멤버십을 다시 읽지 않으므로, 호출하는 쪽의
   * 멤버십 스냅샷과 project 목록이 항상 같은 기준을 갖습니다(PR #42 리뷰의 role 누락 경합 대응).
   */
  List<ProjectSnapshot> listByWorkspaceIds(Collection<UUID> workspaceIds);
}
