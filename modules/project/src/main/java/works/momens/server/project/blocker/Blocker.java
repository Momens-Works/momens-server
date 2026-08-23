package works.momens.server.project.blocker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import works.momens.server.common.persistence.BaseEntity;

/** 레거시 {@code blockers} 테이블의 읽기 전용 매핑. */
@Getter
@Entity
@Immutable
@Table(name = "blockers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Blocker extends BaseEntity {

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false)
  private String status;

  @Column(name = "blocked_entity_type", nullable = false)
  private String blockedEntityType;

  @Column(name = "blocked_entity_id", nullable = false, columnDefinition = "uuid")
  private UUID blockedEntityId;

  @Column(name = "resolved_at")
  private Instant resolvedAt;
}
