package works.momens.server.mobile.roster;

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
class ProjectMemberService {

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
    // 멤버십은 여기서 한 번만 읽고, 접근 검사와 응답 목록을 같은 스냅샷으로 판단한다(bootstrap의
    // role 누락 경합과 같은 원칙). isMember로 따로 검사하면 READ_COMMITTED에서는 문장마다 최신
    // 커밋을 봐서, 검사와 목록 조회 사이에 멤버십이 회수된 사용자가 목록을 받아 갈 수 있다.
    List<WorkspaceMembership> memberships = workspaceAccess.listMemberships(workspaceId);
    boolean callerIsMember =
        memberships.stream().anyMatch(membership -> membership.userId().equals(userId));
    if (!callerIsMember) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("project_id", projectId.toString()));
    }
    List<UUID> memberIds = memberships.stream().map(WorkspaceMembership::userId).toList();
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
