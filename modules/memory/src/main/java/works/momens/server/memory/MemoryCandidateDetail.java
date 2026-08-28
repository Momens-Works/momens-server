package works.momens.server.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 웹 응답이 요구하는 후보 한 건의 조회 결과.
 *
 * <p>레거시 후보 응답의 필드를 모두 담습니다. 값은 모두 레거시가 저장한 것을 그대로 읽으며 파생 정책이 없습니다.
 *
 * <p>소프트 삭제 필드가 없습니다. {@code memory_candidates}에 컬럼 자체가 없고, 레거시도 후보를 지우는 대신 {@code REJECTED}·{@code
 * EXPIRED} 상태로만 다룹니다.
 *
 * @param confidence 값이 없으면 {@code null}입니다. 레거시 컬럼이 nullable입니다.
 * @param importance 값이 없으면 {@code null}입니다. 목록 정렬의 1순위 키이며, 비어 있는 후보가 뒤로 갑니다.
 * @param sourceRefIds 행이 없으면 <b>빈 목록</b>입니다. 레거시는 SQL {@code NULL}과 빈 배열을 모두 JSON에서 키 생략으로 내보내므로 둘을
 *     구분하지 않습니다. 응답 직렬화에서 빈 목록을 {@code []}로 내보내지 않도록 주의합니다(웹 snapshot 계약 4.2).
 * @param relatedEntityIds {@code sourceRefIds}와 같은 규칙입니다.
 * @param metadata 레거시가 저장한 JSONB입니다. 값이 없으면 {@code null}입니다.
 */
public record MemoryCandidateDetail(
    UUID id,
    UUID workspaceId,
    String label,
    String candidateType,
    String title,
    String summary,
    String body,
    Double confidence,
    Double importance,
    String status,
    List<UUID> sourceRefIds,
    List<UUID> relatedEntityIds,
    String proposedBy,
    Instant reviewedAt,
    UUID reviewedByUserId,
    String rejectionReason,
    Instant expiresAt,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt) {

  public MemoryCandidateDetail {
    // 정규화만 하고 방어적 복사는 하지 않습니다. List.copyOf 는 원소가 null 이면 NPE 를 던지는데,
    // uuid[] 는 원소 null 을 허용하므로 값 하나 때문에 snapshot 응답 전체가 죽을 수 있습니다.
    // ProjectDetail 이 metadata 를 복사 없이 노출하는 것과도 같은 결입니다.
    sourceRefIds = sourceRefIds == null ? List.of() : sourceRefIds;
    relatedEntityIds = relatedEntityIds == null ? List.of() : relatedEntityIds;
  }
}
