package works.momens.server.project.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 레거시 소유 task_updates 읽기 전용 매핑입니다. */
@Getter
@Entity
@Immutable
@Table(name = "task_updates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class TaskUpdate {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
  private UUID projectId;

  @Column(name = "task_id", nullable = false, columnDefinition = "uuid")
  private UUID taskId;

  @Column(name = "author_id", columnDefinition = "uuid")
  private UUID authorId;

  @Column(nullable = false)
  private String body;

  @Column(nullable = false)
  private String kind;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
