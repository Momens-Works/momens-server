package works.momens.server.project.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import works.momens.server.common.persistence.BaseEntity;

/**
 * 마일스톤.
 *
 * <p>레거시 {@code momens-api}의 {@code milestones} 테이블과 호환됩니다.
 *
 * <p>이 서버에는 쓰기 경로가 전혀 없습니다(쓰기는 MOM-0866). 그래서 {@link Project}처럼 일부 컬럼만 읽기 전용으로 두는 게 아니라 엔티티 전체를
 * {@link Immutable}로 둡니다. 조회도 이 엔티티가 아니라 {@link works.momens.server.project.MilestoneReader}의 DTO
 * projection이 담당합니다. 그래도 매핑해 두는 것은 prod에서 {@code ddl-auto=validate}가 공유 스키마와의 어긋남을 기동 시점에 잡게 하기
 * 위해서입니다.
 *
 * <p>{@code workspace_id}가 없습니다. 워크스페이스는 {@code project_id}를 거쳐서만 알 수 있어 조회가 {@link Project}를
 * 조인합니다. {@code label}과 {@code metadata}도 없어 project와 필드 집합이 다릅니다.
 *
 * <p>{@code progress}는 project와 달리 매핑합니다. 레거시 write 경로가 실제로 유지하는 값이고 웹이 그대로 읽습니다. 저장값을 쓰지 않기로 한
 * 결정(ADR-0013)은 {@code projects.progress}에만 해당합니다.
 *
 * <p>{@code status}와 {@code health_status}는 base persistence 단계라 문자열로만 둡니다. DB CHECK 제약이 허용값을
 * 강제합니다.
 */
@Getter
@Entity
@Immutable
@Table(name = "milestones")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Milestone extends BaseEntity {

  @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
  private UUID projectId;

  @Column(nullable = false)
  private String name;

  @Column private String description;

  @Column(name = "target_date")
  private LocalDate targetDate;

  @Column(nullable = false)
  private String status;

  @Column(name = "health_status", nullable = false)
  private String healthStatus;

  @Column(nullable = false)
  private int progress;

  @Column private String summary;

  @Column(name = "last_context_at")
  private Instant lastContextAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
