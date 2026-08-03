package works.momens.server.minsu.internal.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 비동기 생성의 설정 축(docs/design/minsu-async-task-draft-design.md 11.2절).
 *
 * <p>축은 셋이고 provider 축은 기존 {@link MinsuTaskDraftProperties#enabled()}가 그대로 담당한다(모델 호출 활성 여부). 여기서는
 * 비동기 도입으로 새로 생기는 두 축과 나이 상한 값을 갖는다.
 *
 * <ul>
 *   <li>{@code enroll} — 신규 convert가 원장을 적재하는가. 끄면 신규는 동기 경로로 가고 기존 원장은 그대로 처리된다.
 *   <li>{@code drain} — scheduler가 원장을 처리하는가. 끄면 기존 원장이 멈추고 {@code read_deadline_at}이 지나면 읽기 투영이
 *       {@code ready}로 닫는다.
 * </ul>
 *
 * <p>축 사이에는 파생 규칙이 하나 있다. <b>provider가 비활성이면 {@code enroll} 값과 무관하게 적재하지 않는다</b>(11.2절 표, 9.2절
 * {@code disabled} 행). 운영자가 두 값을 맞춰 끄는 것이 아니라 적재 여부를 {@code provider.enabled && enroll}로 판정해 코드가
 * 강제한다. 이 판정을 쓰는 코드는 MOM-0818(적재)과 MOM-0819(drain)에 붙는다.
 *
 * <p>provider가 활성이지만 <b>설정이 무효한 경우는 적재는 하되 claim하지 않는다</b>(11.2절). 설정을 고치면 그대로 이어서 처리할 수 있는 작업이므로
 * 종료로 기록하지 않고, 그 전에 {@code read_deadline_at}이 지나면 읽기 투영이 닫는다. 따라서 설정 오류의 1차 관측은 원장이 아니라 {@code
 * momens.minsu.llm.config.valid} gauge다({@link MinsuConfigStatus}). 종료 사유 {@code invalid_config}는
 * claim 이후 설정이 무효해진 경합에서만 도달한다.
 *
 * <p>{@code generationDeadline}과 {@code applyMargin}은 적재 시점에 두 시각으로 환산해 원장에 저장한다(8.6절). 조회할 때마다 현재
 * 설정으로 다시 계산하면 설정 변경이 과거 작업의 상태를 뒤집으므로, 이 값 변경은 <b>새로 적재되는 원장에만</b> 적용된다.
 *
 * @param generationDeadline 적재 시각 기준 나이 상한. 재시도 백오프 총합보다 충분히 크게 잡는다(9.1절의 부등식)
 * @param applyMargin 읽기 투영과 반영 CAS 사이의 가드 밴드. 반영 트랜잭션이 조건 평가부터 커밋까지 걸리는 시간보다 커야 한다
 */
@Validated
@ConfigurationProperties("momens.minsu.task-draft.async")
public record MinsuAsyncProperties(
    @DefaultValue("false") boolean enroll,
    @DefaultValue("false") boolean drain,
    @DefaultValue("1h") Duration generationDeadline,
    @DefaultValue("5m") Duration applyMargin) {

  /**
   * 두 시각의 순서를 부팅 시점에 강제한다.
   *
   * <p>margin이 0이거나 상한 이상이면 {@code apply_cutoff_at < read_deadline_at} CHECK에 걸려 적재가 전부 실패하는데, 적재는
   * convert 트랜잭션 안이므로 그 실패가 <b>사용자 요청 실패</b>로 처음 드러난다. 값이 잘못됐다는 사실은 요청이 아니라 부팅에서 알아야 한다.
   */
  public MinsuAsyncProperties {
    if (applyMargin.isNegative() || applyMargin.isZero()) {
      throw new IllegalArgumentException("momens.minsu.task-draft.async.apply-margin은 0보다 커야 합니다");
    }
    if (applyMargin.compareTo(generationDeadline) >= 0) {
      throw new IllegalArgumentException(
          "momens.minsu.task-draft.async.apply-margin은 generation-deadline보다 작아야 합니다");
    }
  }
}
