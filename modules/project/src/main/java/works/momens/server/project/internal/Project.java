package works.momens.server.project.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import works.momens.server.common.persistence.BaseEntity;

/**
 * 프로젝트.
 *
 * <p>레거시 {@code momens-api}의 {@code projects} 테이블과 호환됩니다.
 *
 * <p>{@code label}, {@code healthStatus}, {@code unresolvedCount}, {@code vocSignalCount}, {@code
 * lastContextAt}, {@code metadata}는 웹 이관(MOM-0857)이 추가한 레거시 read 전용 컬럼입니다. 이 서버는 이 값들을 계산하지도 쓰지도
 * 않고, 조회도 이 엔티티가 아니라 {@link works.momens.server.project.ProjectDetailReader}의 DTO projection이
 * 담당합니다. 그래도 매핑해 두는 것은 prod에서 {@code ddl-auto=validate}가 공유 스키마와의 어긋남을 기동 시점에 잡게 하기 위해서입니다.
 *
 * <p>{@code progress} 컬럼은 매핑하지 않습니다.
 *
 * <p>진행률은 태스크를 기준으로 계산하므로(MOM-0800, ADR-0013), 저장된 {@code progress} 값은 사용하지 않습니다. 컬럼은 레거시 웹 호환을 위해
 * DB에 그대로 유지합니다.
 *
 * <p>{@code status}는 base persistence 단계라 문자열로만 둡니다. DB CHECK 제약이 {@code active}/{@code archived}만
 * 허용합니다.
 */
@Getter
@Entity
@Table(name = "projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Project extends BaseEntity {

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(nullable = false)
  private String name;

  @Column private String description;

  @Column(nullable = false)
  private String status;

  @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
  private UUID ownerId;

  @Column(name = "target_date")
  private LocalDate targetDate;

  @Column private String summary;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column private String label;

  @Column(name = "health_status", nullable = false)
  private String healthStatus;

  @Column(name = "unresolved_count", nullable = false)
  private int unresolvedCount;

  @Column(name = "voc_signal_count", nullable = false)
  private int vocSignalCount;

  @Column(name = "last_context_at")
  private Instant lastContextAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @Builder
  private Project(
      UUID workspaceId,
      String name,
      String description,
      String status,
      UUID ownerId,
      LocalDate targetDate,
      String summary) {
    this.workspaceId = workspaceId;
    this.name = name;
    this.description = description;
    // INSERT 시 엔티티 값이 DB DEFAULT 보다 우선하므로, 레거시 기본값 'active'를 앱 생성에서도 보장한다.
    this.status = status != null ? status : "active";
    this.ownerId = ownerId;
    this.targetDate = targetDate;
    this.summary = summary;
    // status와 같은 이유로, NOT NULL DEFAULT를 가진 레거시 컬럼의 기본값을 앱 생성에서도 보장한다.
    this.healthStatus = "open";
  }
}
