package works.momens.server.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * project 모듈의 상세 조회 public API.
 *
 * <p>레거시 프로젝트 응답의 필드를 모두 담은 {@link ProjectDetail}을 돌려줍니다. 모바일이 쓰는 {@link ProjectReader}와 나눠 둔 것은 두
 * 소비 표면의 투영 폭이 다르기 때문입니다({@code ProjectReader}는 5필드 스냅샷과 권한 검사용 조회를 담당합니다).
 *
 * <p>소프트 삭제된 project는 모든 메서드에서 없는 것으로 취급합니다. project가 없을 때 반환할 에러는 호출하는 쪽에서 결정합니다.
 */
public interface ProjectDetailReader {

  /** project 한 건의 상세를 조회합니다. */
  Optional<ProjectDetail> findDetail(UUID projectId);

  /** workspaceId에 속한 project를 모두 조회합니다. 정렬은 레거시와 같은 생성 시각 내림차순입니다. */
  List<ProjectDetail> listDetailsByWorkspaceId(UUID workspaceId);
}
