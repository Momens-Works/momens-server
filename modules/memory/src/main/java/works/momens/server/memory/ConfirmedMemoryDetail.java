package works.momens.server.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 웹 응답이 요구하는 확정 메모리 한 건의 조회 결과.
 *
 * <p>레거시 메모리 응답의 필드를 모두 담습니다. 값은 모두 레거시가 저장한 것을 그대로 읽으며 파생 정책이 없습니다.
 *
 * <p>snapshot의 {@code memories} 구획과 {@code task_contexts[].memories}가 <b>같은 이 모델</b>을 씁니다. 레거시는 번들
 * 쪽을 10개 컬럼만 채워 좁게 내보내지만(웹 snapshot 계약 2.4), 신규는 둘을 넓은 쪽으로 통일합니다. 메모리는 응답 안에서 중복 출처라, 좁게 두면 클라이언트가
 * 두 출처를 합칠 때 좁은 쪽이 넓은 쪽을 덮어써 관계 정보가 사라지기 때문입니다(계약 4.5, MOM-0876). 그래서 좁은 전용 모델을 두지 않습니다.
 *
 * <p>{@code deletedAt}은 담지 않습니다. 조회가 소프트 삭제된 메모리를 제외하므로 항상 비어 있습니다.
 *
 * @param sourceRefIds 행이 없으면 <b>빈 목록</b>입니다. 레거시는 SQL {@code NULL}과 빈 배열을 모두 JSON에서 키 생략으로 내보내므로 둘을
 *     구분하지 않습니다. 응답 직렬화에서 빈 목록을 {@code []}로 내보내지 않도록 주의합니다(웹 snapshot 계약 4.2).
 * @param relatedEntityIds {@code sourceRefIds}와 같은 규칙입니다.
 * @param metadata 레거시가 저장한 JSONB입니다. 값이 없으면 {@code null}입니다.
 */
public record ConfirmedMemoryDetail(
    UUID id,
    UUID workspaceId,
    String label,
    String memoryType,
    String title,
    String summary,
    String body,
    String status,
    List<UUID> sourceRefIds,
    List<UUID> relatedEntityIds,
    UUID createdFromCandidateId,
    UUID confirmedByUserId,
    Instant confirmedAt,
    Instant validFrom,
    Instant validUntil,
    Instant invalidatedAt,
    UUID invalidatedByUserId,
    String invalidationReason,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt) {

  public ConfirmedMemoryDetail {
    // 정규화만 하고 방어적 복사는 하지 않습니다. List.copyOf 는 원소가 null 이면 NPE 를 던지는데,
    // uuid[] 는 원소 null 을 허용하므로 값 하나 때문에 snapshot 응답 전체가 죽을 수 있습니다.
    // ProjectDetail 이 metadata 를 복사 없이 노출하는 것과도 같은 결입니다.
    sourceRefIds = sourceRefIds == null ? List.of() : sourceRefIds;
    relatedEntityIds = relatedEntityIds == null ? List.of() : relatedEntityIds;
  }
}
