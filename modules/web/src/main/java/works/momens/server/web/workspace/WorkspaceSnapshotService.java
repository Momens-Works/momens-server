package works.momens.server.web.workspace;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.context.EntityRelationReader;
import works.momens.server.context.TaskContextLinks;
import works.momens.server.memory.ConfirmedMemoryDetail;
import works.momens.server.memory.ConfirmedMemoryReader;
import works.momens.server.memory.MemoryCandidateDetail;
import works.momens.server.memory.MemoryCandidateReader;
import works.momens.server.project.BlockerDetail;
import works.momens.server.project.BlockerReader;
import works.momens.server.project.MilestoneDetail;
import works.momens.server.project.MilestoneReader;
import works.momens.server.project.ProjectDetail;
import works.momens.server.project.ProjectDetailReader;
import works.momens.server.project.TaskReader;
import works.momens.server.project.WebTaskDetail;
import works.momens.server.source.LegacySourceRefDetail;
import works.momens.server.source.SourceRefReader;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;
import works.momens.server.web.workspace.dto.response.WorkspaceSnapshotResponse;
import works.momens.server.workspace.WorkspaceAccess;
import works.momens.server.workspace.WorkspaceDetail;
import works.momens.server.workspace.WorkspaceErrorCode;
import works.momens.server.workspace.WorkspaceMembershipDetail;
import works.momens.server.workspace.WorkspaceMembershipReader;
import works.momens.server.workspace.WorkspaceReader;

/** 웹 보드가 한 번의 요청으로 읽는 workspace snapshot을 조합합니다. */
@Service
@RequiredArgsConstructor
class WorkspaceSnapshotService {

  private final WorkspaceReader workspaceReader;
  private final WorkspaceAccess workspaceAccess;
  private final WorkspaceMembershipReader workspaceMembershipReader;
  private final UserService userService;
  private final ProjectDetailReader projectDetailReader;
  private final MilestoneReader milestoneReader;
  private final TaskReader taskReader;
  private final BlockerReader blockerReader;
  private final MemoryCandidateReader memoryCandidateReader;
  private final ConfirmedMemoryReader confirmedMemoryReader;
  private final EntityRelationReader entityRelationReader;
  private final SourceRefReader sourceRefReader;

  @Transactional(readOnly = true)
  public WorkspaceSnapshotResponse get(UUID workspaceId, UUID userId) {
    WorkspaceDetail workspace =
        workspaceReader
            .findById(workspaceId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        WorkspaceErrorCode.WORKSPACE_NOT_FOUND,
                        Map.of("workspace_id", workspaceId.toString())));
    if (!workspaceAccess.isMember(workspaceId, userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("workspace_id", workspaceId.toString()));
    }

    List<WorkspaceMembershipDetail> memberships =
        workspaceMembershipReader.listDetailsByWorkspaceId(workspaceId);
    Map<UUID, UserProfile> profiles =
        userService
            .getProfiles(memberships.stream().map(WorkspaceMembershipDetail::userId).toList())
            .stream()
            .collect(Collectors.toMap(UserProfile::id, Function.identity()));
    List<WorkspaceMemberView> members =
        memberships.stream()
            .filter(membership -> profiles.containsKey(membership.userId()))
            .map(
                membership -> {
                  UserProfile profile = profiles.get(membership.userId());
                  return new WorkspaceMemberView(
                      profile.id(),
                      profile.email(),
                      profile.name(),
                      membership.role(),
                      membership.createdAt(),
                      membership.updatedAt());
                })
            .sorted(
                Comparator.comparing(WorkspaceMemberView::createdAt)
                    .thenComparing(WorkspaceMemberView::userId))
            .toList();
    List<ProjectDetail> projects = projectDetailReader.listDetailsByWorkspaceId(workspaceId);
    List<MilestoneDetail> milestones = milestoneReader.listDetailsByWorkspaceId(workspaceId);
    List<WebTaskDetail> tasks = taskReader.listWebDetailsByWorkspaceId(workspaceId);
    List<BlockerDetail> blockers = blockerReader.listDetailsByWorkspaceId(workspaceId);
    List<MemoryCandidateDetail> candidates =
        memoryCandidateReader.listDetailsByWorkspaceId(workspaceId);
    List<ConfirmedMemoryDetail> memories =
        confirmedMemoryReader.listDetailsByWorkspaceId(workspaceId);

    Map<UUID, TaskContextLinks> linksByTaskId =
        entityRelationReader.findTaskContextLinks(
            workspaceId, tasks.stream().map(WebTaskDetail::id).toList());
    LinkedHashSet<UUID> memoryIds = new LinkedHashSet<>();
    LinkedHashSet<UUID> sourceRefIds = new LinkedHashSet<>();
    linksByTaskId
        .values()
        .forEach(
            links -> {
              memoryIds.addAll(links.memoryIds());
              sourceRefIds.addAll(links.sourceRefIds());
            });
    List<ConfirmedMemoryDetail> linkedMemories =
        confirmedMemoryReader.findDetailsByIds(workspaceId, memoryIds);
    List<LegacySourceRefDetail> linkedSourceRefs =
        sourceRefReader.findLegacyDetailsByIds(workspaceId, sourceRefIds);

    return WorkspaceSnapshotResponse.from(
        workspace,
        members,
        projects,
        milestones,
        tasks,
        blockers,
        candidates,
        memories,
        tasks.stream()
            .filter(task -> linksByTaskId.containsKey(task.id()))
            .map(
                task -> {
                  TaskContextLinks links = linksByTaskId.get(task.id());
                  return new WorkspaceSnapshotResponse.TaskContextResponse(
                      task.id(),
                      linkedMemories.stream()
                          .filter(memory -> links.memoryIds().contains(memory.id()))
                          .map(WorkspaceSnapshotResponse.MemoryResponse::from)
                          .toList(),
                      linkedSourceRefs.stream()
                          .filter(sourceRef -> links.sourceRefIds().contains(sourceRef.id()))
                          .map(WorkspaceSnapshotResponse.SourceRefResponse::from)
                          .toList());
                })
            .toList());
  }
}
