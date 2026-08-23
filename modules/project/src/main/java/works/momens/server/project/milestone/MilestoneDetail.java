package works.momens.server.project.milestone;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 웹 응답이 요구하는 마일스톤 한 건의 조회 결과.
 *
 * <p>레거시 마일스톤 응답의 필드를 모두 담습니다. 값은 모두 레거시가 저장한 것을 그대로 읽으며 파생 정책이 없습니다.
 *
 * <p>{@code progress}는 담습니다. {@link ProjectDetail}이 같은 이름의 필드를 빼는 것과 다릅니다. 레거시 write 경로가 마일스톤 진행률은
 * 실제로 유지하고 웹이 그 값을 읽으며, 저장값을 쓰지 않기로 한 결정(ADR-0013)은 {@code projects.progress}에만 해당하기 때문입니다.
 *
 * <p>{@code workspaceId}는 담지 않습니다. 레거시 {@code milestones}에 컬럼이 없고 응답에도 없습니다. 소속은 {@code projectId}로
 * 거슬러 올라가 알아냅니다.
 *
 * <p>{@code deletedAt}도 담지 않습니다. 조회가 소프트 삭제된 마일스톤을 제외하므로 항상 비어 있습니다.
 *
 * @param ownerUserIds {@code milestone_owners}를 {@code created_at, owner_user_id} 순으로 모은 값입니다. 행이
 *     없으면 <b>빈 목록</b>입니다. project와 달리 폴백할 소유자 컬럼이 없기 때문입니다. 레거시는 이 경우 JSON에서 {@code owner_user_ids}
 *     키 자체를 생략하므로(웹 snapshot 계약 2.3), 응답 직렬화에서 빈 목록을 {@code []}로 내보내지 않도록 주의합니다.
 */
public record MilestoneDetail(
    UUID id,
    UUID projectId,
    String name,
    String description,
    LocalDate targetDate,
    String status,
    List<UUID> ownerUserIds,
    String healthStatus,
    int progress,
    String summary,
    Instant lastContextAt,
    Instant createdAt,
    Instant updatedAt) {}
