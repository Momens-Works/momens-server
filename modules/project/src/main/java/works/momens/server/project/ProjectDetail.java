package works.momens.server.project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 웹 응답이 요구하는 project 한 건의 조회 결과.
 *
 * <p>모바일의 {@link ProjectSnapshot}과 달리 레거시 프로젝트 응답의 필드를 모두 담습니다. 두 모델을 합치지 않는 것은 소비하는 use case가 다르기
 * 때문입니다. 값은 모두 레거시가 저장한 것을 그대로 읽으며 파생 정책이 없습니다.
 *
 * <p>{@code progress}는 담지 않습니다. 레거시 응답에는 항상 실리지만 웹이 그 값을 읽지 않고 태스크에서 직접 계산하며(momens-fe의 {@code
 * mapProject}가 이 필드를 매핑하지 않습니다), 이 서버도 저장값을 쓰지 않기로 했습니다(ADR-0013). 진행률이 필요한 소비자가 생기면 {@link
 * ProjectReader#progressOf(UUID)}를 씁니다.
 *
 * <p>{@code deletedAt}도 담지 않습니다. 조회가 소프트 삭제된 project를 제외하므로 항상 비어 있습니다.
 *
 * @param ownerUserIds {@code project_owners}를 {@code created_at, owner_user_id} 순으로 모은 값입니다. 행이 없으면
 *     {@code ownerId} 하나만 담습니다(레거시 COALESCE 폴백). 따라서 절대 비지 않습니다.
 * @param metadata 레거시가 저장한 JSONB입니다. 값이 없으면 {@code null}입니다.
 */
public record ProjectDetail(
    UUID id,
    UUID workspaceId,
    String label,
    String name,
    String description,
    String status,
    UUID ownerId,
    List<UUID> ownerUserIds,
    LocalDate targetDate,
    String healthStatus,
    String summary,
    int unresolvedCount,
    int vocSignalCount,
    Instant lastContextAt,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt) {}
