package works.momens.server.project.core.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import works.momens.server.project.core.ProjectDetail;

/**
 * {@code projects} 한 행의 DTO projection.
 *
 * <p>{@link ProjectDetail}에서 {@code ownerUserIds}만 뺀 모양입니다. 소유자는 {@code project_owners}를 배치로 한 번 읽어
 * 채우므로(웹 snapshot 계약 4.7), 행 조회와 소유자 조회를 나눠 두고 여기서 합칩니다.
 *
 * <p>{@link Project} 엔티티를 반환하지 않는 것은 read 경로가 애그리거트를 통과할 필요가 없기 때문입니다. constructor expression은 필요한
 * 컬럼만 SELECT하고 영속성 컨텍스트에 엔티티를 올리지 않습니다.
 */
record ProjectDetailRow(
    UUID id,
    UUID workspaceId,
    String label,
    String name,
    String description,
    String status,
    UUID ownerId,
    LocalDate targetDate,
    String healthStatus,
    String summary,
    int unresolvedCount,
    int vocSignalCount,
    Instant lastContextAt,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt) {

  ProjectDetail toDetail(List<UUID> ownerUserIds) {
    return new ProjectDetail(
        id,
        workspaceId,
        label,
        name,
        description,
        status,
        ownerId,
        ownerUserIds,
        targetDate,
        healthStatus,
        summary,
        unresolvedCount,
        vocSignalCount,
        lastContextAt,
        metadata,
        createdAt,
        updatedAt);
  }
}
