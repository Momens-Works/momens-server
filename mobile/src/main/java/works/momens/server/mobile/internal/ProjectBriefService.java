package works.momens.server.mobile.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.project.ProjectReader;
import works.momens.server.project.ProjectSnapshot;
import works.momens.server.signal.SignalListService;
import works.momens.server.signal.SignalSummary;
import works.momens.server.signal.SignalSummaryPage;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * 모바일 브리프 표면의 조합 서비스. project(스냅샷), signal(미처리 요약), workspace(멤버십) public API를 조합하고 도메인 정책을 소유하지
 * 않습니다.
 *
 * <p>project 조회 결과가 workspace id를 포함하므로(태스크 상세와 같은 방식) project가 있는지 확인한 뒤 workspace를 따로 조회하지 않고 바로
 * 멤버십을 검사합니다. signal 목록 서비스도 자체적으로 접근을 검사해 브리프의 검사와 일부 겹치지만, 단순 조회 수준이라 그대로 두었습니다.
 *
 * <p>노출 필터와 라벨, change(VOC) 제외, 페이지 기본 크기는 모바일 조합 규칙이라 이 서비스와 {@link BriefSignalFilter}가 소유합니다.
 */
@Service
@RequiredArgsConstructor
public class ProjectBriefService {

  /** 시그널 요약 기본 페이지 크기. 화면 기본 노출 3개(2026-07-10 화면설계서)와 같습니다. */
  private static final int DEFAULT_PAGE_SIZE = 3;

  private final ProjectReader projectReader;
  private final WorkspaceAccess workspaceAccess;
  private final SignalListService signalListService;

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
        snapshot, toFilterCounts(countsByType), toItems(firstPage.items()), firstPage.nextCursor());
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
}
