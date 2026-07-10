package works.momens.server.mobile.internal;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.BoardTask;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.project.ProjectReader;
import works.momens.server.project.ProjectSnapshot;
import works.momens.server.project.TaskReader;
import works.momens.server.signal.SignalListService;
import works.momens.server.signal.SignalSummary;
import works.momens.server.signal.SignalSummaryPage;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * 모바일 브리프 표면의 조합 서비스. project(스냅샷, 태스크), signal(미처리 요약), workspace(멤버십) public API를 조합하고 도메인 정책을
 * 소유하지 않습니다.
 *
 * <p>project 조회 결과가 workspace id를 포함하므로(태스크 상세와 같은 방식) project가 있는지 확인한 뒤 workspace를 따로 조회하지 않고 바로
 * 멤버십을 검사합니다. signal 목록 서비스도 자체적으로 접근을 검사해 브리프의 검사와 일부 겹치지만, 단순 조회 수준이라 그대로 두었습니다.
 *
 * <p>노출 필터와 라벨, change(VOC) 제외, 페이지 기본 크기, 현재 우선순위 구성(후보 상태와 정렬, 상위 4개)은 모바일 조합 규칙이므로 이 서비스와 {@link
 * BriefSignalFilter}, {@link MobilePriority}가 소유합니다.
 */
@Service
@RequiredArgsConstructor
public class ProjectBriefService {

  /** 시그널 요약 기본 페이지 크기. 화면 기본 노출 3개(2026-07-10 화면설계서)와 같습니다. */
  private static final int DEFAULT_PAGE_SIZE = 3;

  /** 현재 우선순위 최대 개수. 화면의 상위 4개 표기와 같습니다(2026-07-10 화면설계서). */
  private static final int PRIORITY_LIMIT = 4;

  /** 현재 우선순위 후보 상태. 진행 중인 todo와 in_progress만 담고 backlog와 완료 상태는 제외합니다(2026-07-10 기획 확정). */
  private static final List<String> PRIORITY_STATUSES = List.of("todo", "in_progress");

  /**
   * 우선순위 정렬. priority 높은 순이고, 같으면 생성 오래된 순, 생성 시각까지 같으면 id 순으로 고정합니다. id 비교는 canonical hex 문자열로
   * 합니다. PostgreSQL uuid 정렬(바이트 순서)과 같아서 저장소 정렬과 어긋나지 않습니다(signal 커서와 같은 이유).
   */
  private static final Comparator<BoardTask> PRIORITY_ORDER =
      Comparator.comparing((BoardTask task) -> MobilePriority.fromStored(task.priority()))
          .thenComparing(BoardTask::createdAt)
          .thenComparing(task -> task.id().toString());

  private final ProjectReader projectReader;
  private final WorkspaceAccess workspaceAccess;
  private final SignalListService signalListService;
  private final TaskReader taskReader;

  @Transactional(readOnly = true)
  public MobileBrief getBrief(UUID projectId, UUID userId) {
    ProjectSnapshot snapshot =
        projectReader
            .findSnapshot(projectId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ProjectErrorCode.PROJECT_NOT_FOUND,
                        Map.of("project_id", projectId.toString())));
    if (!workspaceAccess.isMember(snapshot.workspaceId(), userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("project_id", projectId.toString()));
    }
    Map<String, Long> countsByType = signalListService.countUnprocessedByType(projectId, userId);
    SignalSummaryPage firstPage =
        signalListService.listUnprocessedPage(
            projectId, userId, BriefSignalFilter.ALL.signalTypes(), null, DEFAULT_PAGE_SIZE);
    return new MobileBrief(
        snapshot,
        toFilterCounts(countsByType),
        toItems(firstPage.items()),
        firstPage.nextCursor(),
        toPriorities(taskReader.listTasksByStatus(projectId, PRIORITY_STATUSES)));
  }

  @Transactional(readOnly = true)
  public MobileBriefSignalPage getSignalSummaryPage(
      UUID projectId, UUID userId, String filterKey, String cursor, Integer limit) {
    BriefSignalFilter filter =
        BriefSignalFilter.fromKey(filterKey)
            .orElseThrow(
                () ->
                    new BusinessException(
                        CommonErrorCode.COMMON_VALIDATION_FAILED,
                        Map.of("filter", String.valueOf(filterKey))));
    SignalSummaryPage page =
        signalListService.listUnprocessedPage(
            projectId, userId, filter.signalTypes(), cursor, resolvePageSize(limit));
    return new MobileBriefSignalPage(toItems(page.items()), page.nextCursor());
  }

  private static int resolvePageSize(Integer limit) {
    // 미지정이나 0은 기본값으로 두고 에러를 내지 않는다. 음수만 거른다(AIP-158).
    if (limit == null || limit == 0) {
      return DEFAULT_PAGE_SIZE;
    }
    if (limit < 0) {
      throw new BusinessException(
          CommonErrorCode.COMMON_VALIDATION_FAILED, Map.of("limit", limit.toString()));
    }
    return limit;
  }

  private static List<MobileBrief.FilterCount> toFilterCounts(Map<String, Long> countsByType) {
    long exposedTotal =
        BriefSignalFilter.exposedTypes().stream()
            .mapToLong(type -> countsByType.getOrDefault(type, 0L))
            .sum();
    return Arrays.stream(BriefSignalFilter.values())
        .map(
            filter ->
                new MobileBrief.FilterCount(
                    filter,
                    filter == BriefSignalFilter.ALL
                        ? exposedTotal
                        : countsByType.getOrDefault(filter.signalType(), 0L)))
        .toList();
  }

  private static List<MobileBrief.SignalItem> toItems(List<SignalSummary> summaries) {
    return summaries.stream()
        .map(summary -> new MobileBrief.SignalItem(summary.id(), summary.type(), summary.title()))
        .toList();
  }

  private static List<MobileBrief.Priority> toPriorities(List<BoardTask> tasks) {
    List<BoardTask> ordered = tasks.stream().sorted(PRIORITY_ORDER).limit(PRIORITY_LIMIT).toList();
    return IntStream.range(0, ordered.size())
        .mapToObj(
            index ->
                new MobileBrief.Priority(
                    index + 1, ordered.get(index).title(), ordered.get(index).id()))
        .toList();
  }
}
