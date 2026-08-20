package works.momens.server.source;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * source-ref 한 건을 나타냅니다.
 *
 * <p>{@code source_refs}에 저장된 값을 컬럼 누락 없이 모두 포함하며, 레거시의 source-ref 검증 응답과 동일한 필드로 구성됩니다.
 *
 * <p>같은 테이블을 더 적은 컬럼으로 조회하는 타입도 별도로 존재합니다. 해당 타입은 태스크에 연결된 source-ref 목록에 사용하며, 수집한 원문은 포함하지 않습니다.
 */
public record SourceRefDetail(
    UUID id,
    UUID workspaceId,
    String sourceType,
    UUID sourceConnectionId,
    String sourceObjectType,
    String sourceObjectId,
    String sourceUrl,
    String title,
    String text,
    String snippet,
    String authorName,
    String authorEmail,
    Instant sourceCreatedAt,
    Instant sourceUpdatedAt,
    String visibility,
    String permissionKey,
    UUID verifiedByUserId,
    Instant verifiedAt,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt) {}
