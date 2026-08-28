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
 * 브리프의 시그널 요약 문단.
 *
 * <p>민수가 적재하는 {@code signal_digests}를 읽기 전용으로 매핑합니다(@Immutable). 민수 구현 전에는 같은 backing 계약을 따르는
 * fixture가 채웁니다(ADR-0011). 서버가 쓰지 않으므로 식별자를 앱에서 만들지 않고 {@code BaseEntity}를 상속하지 않습니다.
 *
 * <p>{@code Signal}의 목록 카드 요약({@code SignalSummary})과는 다른 값입니다. 이 문단은 시그널 한 건이 아니라 프로젝트의 그날 신호 전체를
 * 요약합니다.
 *
 * <p>하루 단위 값이지만 날짜 컬럼 없이 {@code createdAt}으로 거릅니다. 브리프가 시그널을 거를 때 쓰는 기준과 같아서, 문단과 그 문단이 설명하는 시그널이
 * 어긋날 수 없습니다. 하루 경계를 어떤 타임존으로 볼지는 조회하는 표면이 정합니다.
 */
@Getter
@Entity
@Immutable
@Table(name = "signal_digests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SignalDigest {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
  private UUID projectId;

  @Column(nullable = false)
  private String summary;

  /** 생산 시각. 브리프가 하루 범위로 거르는 기준입니다. */
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** 생산자가 문단을 철회하는 유일한 경로. 조회는 소프트 삭제를 없는 것으로 취급합니다(signals 미러와 같은 규칙). */
  @Column(name = "deleted_at")
  private Instant deletedAt;
}
