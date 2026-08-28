package works.momens.server.project.milestone.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import works.momens.server.project.milestone.MilestoneDetail;

/**
 * {@code milestones} 한 행의 DTO projection.
 *
 * <p>{@link MilestoneDetail}에서 {@code ownerUserIds}만 뺀 모양입니다. 소유자는 {@code milestone_owners}를 배치로 한
 * 번 읽어 채우므로(웹 snapshot 계약 4.7), 행 조회와 소유자 조회를 나눠 두고 여기서 합칩니다.
 *
 * <p>{@link Milestone} 엔티티를 반환하지 않는 것은 read 경로가 애그리거트를 통과할 필요가 없기 때문입니다. constructor expression은
 * 필요한 컬럼만 SELECT하고 영속성 컨텍스트에 엔티티를 올리지 않습니다.
 */
record MilestoneDetailRow(
    UUID id,
    UUID projectId,
    String name,
    String description,
    LocalDate targetDate,
    String status,
    String healthStatus,
    int progress,
    String summary,
    Instant lastContextAt,
    Instant createdAt,
    Instant updatedAt) {

  MilestoneDetail toDetail(List<UUID> ownerUserIds) {
    return new MilestoneDetail(
        id,
        projectId,
        name,
        description,
        targetDate,
        status,
        ownerUserIds,
        healthStatus,
        progress,
        summary,
        lastContextAt,
        createdAt,
        updatedAt);
  }
}
