package works.momens.server.signal.query;

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
 * Signal 원본.
 *
 * <p>worker가 원천 이벤트를 바탕으로 생성하는 Signal의 조회 모델입니다(ADR-0007). 서버는 Signal을 쓰지 않고 읽기만 하므로 {@code
 * BaseEntity}(앱 생성 식별자·감사)를 상속하지 않고 {@link Immutable}로 둡니다. 사용자 처리 이력은 별도 {@code signal_actions}가
 * 소유하고, 화면 표시 라벨은 {@code type}에서 앱이 파생하므로 별도 status 컬럼은 없습니다.
 *
 * <p>운영에서는 공유 DB의 실제 signals를 validate로 검증하고, local/test는 별도 DB의 미러를 사용합니다([데이터]
 * docs/rules/persistence.md). 목록/상세가 읽는 컬럼만 매핑하며 {@code metadata}, {@code updated_at}은 매핑하지 않습니다.
 */
@Getter
@Entity
@Immutable
@Table(name = "signals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Signal {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
  private UUID projectId;

  @Column(nullable = false)
  private String type;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String description;

  @Column private String impact;

  @Column(name = "minsu_suggestion")
  private String minsuSuggestion;

  @Column(name = "occurred_at")
  private Instant occurredAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
