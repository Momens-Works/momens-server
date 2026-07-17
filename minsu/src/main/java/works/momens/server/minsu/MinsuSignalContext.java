package works.momens.server.minsu;

import java.util.List;

/**
 * 민수 생성 입력. Signal의 제목과 근거(evidence)를 담습니다(MOM-0692에서 선택한 title + evidence 입력 범위).
 *
 * <p>소비자(signal 모듈)가 자기 도메인 모델에서 조립해 넘깁니다. 민수는 이 값만으로 생성하므로 signal 모듈에 역의존하지 않습니다. {@code
 * evidence}는 표시 순서대로이며 비어 있을 수 있습니다(worker 미생산).
 */
public record MinsuSignalContext(String title, List<Evidence> evidence) {

  public MinsuSignalContext {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }

  /** 근거 한 건의 의미 값(대상·변화·영향, ADR-0011). 각 값은 null일 수 있습니다. */
  public record Evidence(String target, String change, String impact) {}
}
