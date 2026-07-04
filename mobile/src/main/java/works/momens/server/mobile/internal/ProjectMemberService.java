package works.momens.server.mobile.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.project.ProjectReader;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;
import works.momens.server.workspace.WorkspaceAccess;
import works.momens.server.workspace.WorkspaceMembership;

/**
 * 프로젝트 멤버 조회 조합 서비스. 도메인 모듈 public API 3개(project, workspace, user)만 조합하고 도메인 정책을 소유하지 않습니다.
 *
 * <p>멤버의 범위는 project가 속한 workspace의 멤버십이고(요구사항 명세 권한 요구사항), 검색과 정렬은 조합 규칙이라 이 서비스가 소유합니다. readOnly
 * 트랜잭션을 조합 경계에 두면 안쪽 리더들이 REQUIRED 전파로 합류해 한 트랜잭션에서 일관되게 읽습니다(bootstrap 전례).
 */
@Service
@RequiredArgsConstructor
public class ProjectMemberService {

  private final ProjectReader projectReader;
  private final WorkspaceAccess workspaceAccess;
  private final UserService userService;

  @Transactional(readOnly = true)
  public List<ProjectMember> list(UUID projectId, UUID userId, String query) {
    UUID workspaceId =
        projectReader
            .workspaceIdOf(projectId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ProjectErrorCode.PROJECT_NOT_FOUND,
                        Map.of("project_id", projectId.toString())));
    if (!workspaceAccess.isMember(workspaceId, userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("project_id", projectId.toString()));
    }
    // 멤버십은 여기서 한 번만 읽고, 프로필도 이 스냅샷의 userId 목록으로 조회한다(bootstrap의 role 누락
    // 경합과 같은 원칙). 응답은 프로필 목록 기준이라 두 조회 사이의 불일치로 빈 필드가 생길 구조가 없다.
    List<UUID> memberIds =
        workspaceAccess.listMemberships(workspaceId).stream()
            .map(WorkspaceMembership::userId)
            .toList();
    // 검색은 이름 부분 일치에 대소문자 무시, 정렬은 이름 오름차순(같으면 id 보조)이다. 명세에 없는 세부라
    // 2026-07-04 가결정으로 구현했고 규칙은 docs/spec/mobile-api.md 프로젝트 멤버 절에 적었다.
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    return userService.getProfiles(memberIds).stream()
        .filter(
            profile -> needle.isEmpty() || profile.name().toLowerCase(Locale.ROOT).contains(needle))
        .sorted(Comparator.comparing(UserProfile::name).thenComparing(UserProfile::id))
        .map(profile -> new ProjectMember(profile.id(), profile.name(), profile.avatarUrl()))
        .toList();
  }
}
