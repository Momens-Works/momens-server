package works.momens.server.project.milestone;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.workspace.WorkspaceAccess;
import works.momens.server.workspace.WorkspaceMembership;

/** milestone 생성 시 소유자 목록을 검증합니다. */
@Component
@RequiredArgsConstructor
class MilestoneOwnerMembershipChecker {

  private static final String FIELD_OWNER_USER_IDS = "owner_user_ids";

  private final WorkspaceAccess workspaceAccess;

  void requireWorkspaceMembers(UUID workspaceId, List<UUID> ownerUserIds) {
    if (ownerUserIds.isEmpty()) {
      throw validation();
    }
    Set<UUID> members =
        workspaceAccess.listMemberships(workspaceId).stream()
            .map(WorkspaceMembership::userId)
            .collect(Collectors.toSet());
    long matched = ownerUserIds.stream().distinct().filter(members::contains).count();
    if (matched != ownerUserIds.size()) {
      throw validation();
    }
  }

  private static BusinessException validation() {
    return new BusinessException(
        CommonErrorCode.COMMON_VALIDATION_FAILED, Map.of("field", FIELD_OWNER_USER_IDS));
  }
}
