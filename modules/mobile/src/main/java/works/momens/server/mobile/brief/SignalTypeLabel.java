package works.momens.server.mobile.brief;

/**
 * 브리프 시그널 요약 칩의 화면 라벨을 signal type으로 만드는 단일 출처. type의 첫 글자만 대문자로 바꿔 라벨로 씁니다.
 *
 * <p>타입 목록을 코드에 고정하지 않으므로 민수가 새 type을 만들면 자동으로 대문자 라벨을 얻습니다.
 */
final class SignalTypeLabel {

  /** 전체 칩의 키와 라벨. 특정 type이 아니라 집계라 항상 맨 앞에 둡니다. */
  static final String ALL_KEY = "all";

  static final String ALL_LABEL = "All";

  private SignalTypeLabel() {}

  static String of(String type) {
    return Character.toUpperCase(type.charAt(0)) + type.substring(1);
  }
}
