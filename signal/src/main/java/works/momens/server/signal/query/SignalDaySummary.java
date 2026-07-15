package works.momens.server.signal.query;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 프로젝트 하루 시그널 요약 원본(signal_day_summary).
 *
 * <p>worker(민수) 산출물인 프로젝트별 하루 요약 문단의 조회 모델입니다(MOM-0787). 서버는 이 값을 쓰지 않고 읽기만 하므로 {@code
 * BaseEntity}(앱 생성 식별자·감사)를 상속하지 않고 {@link Immutable}로 둡니다. worker가 아직 구현되지 않은 동안에는 같은 backing 계약의
 * fixture가 이 테이블을 채웁니다(docs/design/mobile-mvp-server-requirements.md "합성/파생 필드 응답 정책").
 */
@Getter
@Entity
@Immutable
@Table(name = "signal_day_summaries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SignalDaySummary {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
  private UUID projectId;

  @Column(name = "summary_date", nullable = false)
  private LocalDate summaryDate;

  @Column(nullable = false)
  private String summary;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
