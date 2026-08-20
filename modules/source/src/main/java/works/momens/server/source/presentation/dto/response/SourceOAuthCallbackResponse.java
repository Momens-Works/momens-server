package works.momens.server.source.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import works.momens.server.source.SourceConnectionDetail;

/**
 * 승인 완료 후 이동할 URL이 설정되지 않은 경우 반환하는 source 연결 정보입니다.
 *
 * <p>{@code :web} 모듈의 연결 응답과 다른 타입 이름을 사용합니다. OpenAPI 문서의 스키마 이름이 하나의 공간에서 관리되므로 이름 충돌을 방지하기 위한
 * 구분입니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceOAuthCallbackResponse(
    UUID id,
    UUID workspaceId,
    String sourceType,
    String status,
    String externalWorkspaceId,
    String externalWorkspaceName,
    UUID connectedByUserId,
    Instant connectedAt,
    long capturesReadCount,
    long candidatesExtractedCount,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt) {

  public static SourceOAuthCallbackResponse from(SourceConnectionDetail detail) {
    return new SourceOAuthCallbackResponse(
        detail.id(),
        detail.workspaceId(),
        detail.sourceType(),
        detail.status(),
        detail.externalWorkspaceId(),
        detail.externalWorkspaceName(),
        detail.connectedByUserId(),
        detail.connectedAt(),
        detail.capturesReadCount(),
        detail.candidatesExtractedCount(),
        detail.metadata(),
        detail.createdAt(),
        detail.updatedAt());
  }
}
