package works.momens.server.mobile.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.mobile.internal.BootstrapContext.AccessibleProject;
import works.momens.server.project.ProjectReader;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;
import works.momens.server.workspace.UserWorkspaceMembership;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * 모바일 진입 컨텍스트 조합 서비스. 도메인 모듈 public API 3개(user, project, workspace)만 조합하고 도메인 정책을 소유하지 않습니다.
 *
 * <p>readOnly 트랜잭션을 여기(조합 경계)에 두면 안쪽 리더들이 REQUIRED 전파로 합류해 한 트랜잭션에서 일관되게 읽습니다. {@code
 * projects[].role}은 별도 project 멤버 테이블 없이 소속 workspace 멤버십 role로 매핑합니다(요구사항 명세 권한 요구사항).
 */
@Service
@RequiredArgsConstructor
public class BootstrapService {

  private final UserService userService;
  private final ProjectReader projectReader;
  private final WorkspaceAccess workspaceAccess;

  @Transactional(readOnly = true)
  public BootstrapContext load(UUID userId) {
    UserProfile me = userService.getProfile(userId);
    Map<UUID, String> roleByWorkspaceId =
        workspaceAccess.listUserMemberships(userId).stream()
            .collect(
                Collectors.toMap(
                    UserWorkspaceMembership::workspaceId, UserWorkspaceMembership::role));
    List<AccessibleProject> projects =
        projectReader.listAccessible(userId).stream()
            .map(
                snapshot ->
                    new AccessibleProject(
                        snapshot.id(),
                        snapshot.name(),
                        roleByWorkspaceId.get(snapshot.workspaceId())))
            .toList();
    // 기본 project는 가장 최근에 만든 것(2026-07-04 가결정, 기획 확인 후 확정). listAccessible이
    // 생성 최신순 정렬을 보장하므로 첫 번째를 쓰고, 하나도 없으면 null과 빈 목록을 그대로 내립니다.
    UUID defaultProjectId = projects.isEmpty() ? null : projects.getFirst().id();
    return new BootstrapContext(me, defaultProjectId, projects);
  }
}
