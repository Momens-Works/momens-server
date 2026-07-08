package works.momens.server.signal.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Signal 근거 연결.
 *
 * <p>worker가 적재하는 {@code signal_evidence}(Signal↔source_refs 조인)를 읽기 전용으로 매핑합니다(@Immutable). 복합 PK
 * (signal_id, source_ref_id)를 {@link Key}로 두고, 표시 순서는 {@code sortOrder}로 읽습니다. 근거 상세(제목·요약 등)는 이
 * 테이블이 아니라 source 모듈이 {@code source_refs}에서 hydrate합니다.
 */
@Getter
@Entity
@Immutable
@Table(name = "signal_evidence")
@IdClass(SignalEvidence.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SignalEvidence {

  @Id
  @Column(name = "signal_id", nullable = false, columnDefinition = "uuid")
  private UUID signalId;

  @Id
  @Column(name = "source_ref_id", nullable = false, columnDefinition = "uuid")
  private UUID sourceRefId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  /** 복합 PK 클래스. 필드명은 엔티티의 {@code @Id} 필드명과 일치해야 합니다. */
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  static class Key implements Serializable {
    private UUID signalId;
    private UUID sourceRefId;
  }
}
