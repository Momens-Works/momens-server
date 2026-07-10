package works.momens.server.mobile.internal;

import java.util.Map;

/**
 * 브리프 시그널 요약 칩의 화면 라벨을 signal type에서 파생하는 단일 출처. 기본은 첫 글자를 대문자로 바꾼 형태이고, 대문자화로 얻을 수 없는 표기만
 * override로 둡니다. 지금은 change의 화면 표기 VOC 하나입니다(2026-07-10 화면설계서).
 *
 * <p>타입 목록을 코드에 고정하지 않으므로 민수가 새 type을 만들면 자동으로 대문자 라벨을 얻습니다. 특별한 표기가 필요한 type만 override에 한 줄 추가합니다.
 */
final class SignalTypeLabel {

  /** 전체 칩의 키와 라벨. 특정 type이 아니라 집계라 항상 맨 앞에 둡니다. */
  static final String ALL_KEY = "all";

  static final String ALL_LABEL = "All";

  private static final Map<String, String> OVERRIDES = Map.of("change", "VOC");

  private SignalTypeLabel() {}

  static String of(String type) {
    String override = OVERRIDES.get(type);
    if (override != null) {
      return override;
    }
    return Character.toUpperCase(type.charAt(0)) + type.substring(1);
  }
}
