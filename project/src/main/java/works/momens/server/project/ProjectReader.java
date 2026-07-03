package works.momens.server.project;

import java.util.List;
import java.util.Optional;
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

  /** userId가 멤버인 workspace의 project를 모두 조회합니다. 정렬은 레거시와 같은 생성 시각 내림차순입니다. */
  List<ProjectSnapshot> listAccessible(UUID userId);
}
