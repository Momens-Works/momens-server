package works.momens.server.mobile.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 브리프 시그널 요약이 노출하는 필터. 필터 키와 화면 라벨(2026-07-10 화면설계서 표기), signal type 매핑을 한곳에 모아 모바일 표면이
 * 소유합니다({@link BoardStatus}와 같은 방식). change(VOC)는 브리프에 노출하지 않으므로 여기 없습니다(docs/spec/mobile-api.md
 * 브리프 절).
 *
 * <p>선언 순서가 곧 필터 칩 순서입니다.
 */
public enum BriefSignalFilter {
  ALL("all", "All", null),
  DECISIONS("decisions", "Decision", "decision"),
  RISKS("risks", "Risk", "risk"),
  QUESTIONS("questions", "Question", "question");

  private final String key;
  private final String label;
  private final String signalType;

  BriefSignalFilter(String key, String label, String signalType) {
    this.key = key;
    this.label = label;
    this.signalType = signalType;
  }

  /** 응답의 filters[].key이자 하위 엔드포인트 filter 쿼리 값입니다. */
  public String key() {
    return key;
  }

  /** 화면에 보이는 칩 라벨입니다. */
  public String label() {
    return label;
  }

  /** 이 필터가 대응하는 signal type. ALL은 특정 type이 없어 null입니다. */
  public String signalType() {
    return signalType;
  }

  /** signal 조회에 넘길 type 목록. ALL은 노출하는 type 전부(change 제외)입니다. */
  public List<String> signalTypes() {
    if (this == ALL) {
      return exposedTypes();
    }
    return List.of(signalType);
  }

  /** 브리프가 노출하는 signal type 전부(change 제외)입니다. */
  public static List<String> exposedTypes() {
    return Arrays.stream(values())
        .filter(filter -> filter.signalType != null)
        .map(BriefSignalFilter::signalType)
        .toList();
  }

  /** filter 쿼리 값을 필터로 해석합니다. 노출하지 않는 값이면 빈 값입니다. */
  public static Optional<BriefSignalFilter> fromKey(String key) {
    return Arrays.stream(values()).filter(filter -> filter.key.equals(key)).findFirst();
  }
}
