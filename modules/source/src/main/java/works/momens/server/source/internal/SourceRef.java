package works.momens.server.source.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 근거 원본 포인터(source_ref).
 *
 * <p>레거시 {@code momens-api}가 소유하는 {@code source_refs} 테이블(000002_retrieval_projection.sql)을 <b>읽기
 * 전용</b>으로 매핑합니다. 이 서버는 source_ref를 쓰지 않으므로 {@code BaseEntity}(앱 생성 식별자·감사)를 상속하지 않고 {@link
 * Immutable}로 둡니다. 근거 카드 조립에 필요한 컬럼만 매핑하며, 매핑하지 않은 레거시 컬럼(source_object_*, author_*, metadata 등)은
 * {@code ddl-auto=validate}에서 무시됩니다.
 *
 * <p>운영에서는 공유 DB의 실제 레거시 테이블을 validate로 검증하고, local/test는 별도 DB에 이 컬럼들만 만든 미러를 사용합니다([데이터]
 * docs/rules/persistence.md).
 */
@Getter
@Entity
@Immutable
@Table(name = "source_refs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SourceRef {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(name = "source_type", nullable = false)
  private String sourceType;

  @Column private String title;

  @Column private String snippet;

  @Column(name = "text")
  private String text;

  @Column(name = "source_url")
  private String sourceUrl;

  @Column(name = "source_created_at")
  private Instant sourceCreatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
