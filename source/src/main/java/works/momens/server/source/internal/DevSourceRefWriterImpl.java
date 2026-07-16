package works.momens.server.source.internal;

import java.sql.Timestamp;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.config.DevOnly;
import works.momens.server.source.DevSourceRefWriter;

/**
 * dev 전용 source_refs insert. {@code @Immutable} 읽기 엔티티({@code SourceRef})를 재사용하지 않는 전용 insert
 * 경로다(docs/design/signal-push-demo-design.md 11.2절). 미러 스키마가 가진 컬럼만 쓴다.
 */
@DevOnly
@Component
@RequiredArgsConstructor
class DevSourceRefWriterImpl implements DevSourceRefWriter {

  private final JdbcClient jdbcClient;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public UUID insert(NewSourceRef sourceRef) {
    UUID id = UUID.randomUUID();
    jdbcClient
        .sql(
            "INSERT INTO source_refs "
                + "(id, workspace_id, source_type, title, snippet, text, source_url, source_created_at) "
                + "VALUES (:id, :workspaceId, :sourceType, :title, :snippet, :text, :sourceUrl, "
                + ":sourceCreatedAt)")
        .param("id", id)
        .param("workspaceId", sourceRef.workspaceId())
        .param("sourceType", sourceRef.sourceType())
        .param("title", sourceRef.title())
        .param("snippet", sourceRef.snippet())
        .param("text", sourceRef.text())
        .param("sourceUrl", sourceRef.sourceUrl())
        .param("sourceCreatedAt", timestampOf(sourceRef))
        .update();
    return id;
  }

  private static Timestamp timestampOf(NewSourceRef sourceRef) {
    return sourceRef.sourceCreatedAt() == null ? null : Timestamp.from(sourceRef.sourceCreatedAt());
  }
}
