package works.momens.server.web.source.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import works.momens.server.source.SourceRefDetail;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceRefResponse(
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
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt) {

  public static SourceRefResponse from(SourceRefDetail detail) {
    return new SourceRefResponse(
        detail.id(),
        detail.workspaceId(),
        detail.sourceType(),
        detail.sourceConnectionId(),
        detail.sourceObjectType(),
        detail.sourceObjectId(),
        detail.sourceUrl(),
        detail.title(),
        detail.text(),
        detail.snippet(),
        detail.authorName(),
        detail.authorEmail(),
        detail.sourceCreatedAt(),
        detail.sourceUpdatedAt(),
        detail.visibility(),
        detail.permissionKey(),
        detail.verifiedByUserId(),
        detail.verifiedAt(),
        detail.metadata(),
        detail.createdAt(),
        detail.updatedAt());
  }
}
