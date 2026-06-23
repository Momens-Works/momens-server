package works.momens.server.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 모든 JPA 엔티티의 공통 베이스.
 *
 * <p>식별자·감사 필드 규칙([데이터] docs/rules/persistence.md)을 한곳에 둡니다.
 *
 * <ul>
 *   <li>PK는 UUID v4이며 앱에서 생성합니다(엔티티 생성 시점에 부여).
 *   <li>{@code created_at}/{@code updated_at}은 JPA Auditing으로 채웁니다(UTC {@link Instant} ↔ {@code
 *       timestamptz}).
 * </ul>
 *
 * <p>PK를 앱에서 미리 부여하면 Spring Data가 {@code save()}를 merge(불필요한 SELECT 후 INSERT)로 처리합니다. {@link
 * Persistable}을 구현해 아직 영속화 전(=감사 필드가 비어 있음)이면 새 엔티티로 판단하게 합니다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseEntity implements Persistable<UUID> {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id = UUID.randomUUID();

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return createdAt == null;
  }
}
