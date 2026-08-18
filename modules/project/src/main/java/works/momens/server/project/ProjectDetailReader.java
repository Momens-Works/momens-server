package works.momens.server.project;

import java.util.List;
import java.util.UUID;

/**
 * project 모듈의 상세 조회 public API.
 *
 * <p>레거시 프로젝트 응답의 필드를 모두 담은 {@link ProjectDetail}을 돌려줍니다. 모바일이 쓰는 {@link ProjectReader}와 나눠 둔 것은 두
 * 소비 표면의 투영 폭이 다르기 때문입니다({@code ProjectReader}는 5필드 스냅샷과 권한 검사용 조회를 담당합니다).
 *
 * <p>소프트 삭제된 project는 없는 것으로 취급합니다.
 *
 * <p>단건 조회는 두지 않습니다. snapshot 합성(MOM-0862)이 목록만 쓰고, 레거시 단건 endpoint(H047)는 웹에서 호출되지 않습니다. 필요해지면 그때
 * 추가합니다.
 */
public interface ProjectDetailReader {

  /** workspaceId에 속한 project를 모두 조회합니다. 정렬은 레거시와 같은 생성 시각 내림차순입니다. */
  List<ProjectDetail> listDetailsByWorkspaceId(UUID workspaceId);
}
