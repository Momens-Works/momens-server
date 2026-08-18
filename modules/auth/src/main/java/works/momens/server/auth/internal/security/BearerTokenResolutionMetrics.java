package works.momens.server.auth.internal.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 보호 체인을 통과한 요청이 어느 전송 수단으로 Bearer 토큰을 실어 왔는지 세는 지표.
 *
 * <p><b>인증 성공이 아니라 해석 경로에 도달한 사실을 셉니다.</b> 계측 자리가 resolver라 JWT 디코딩과 만료 검증보다 앞입니다. 따라서 만료된 레거시 쿠키만
 * 붙여 보내 401을 받는 요청도 {@code legacy_session_cookie}로 잡히고, 공개 경로가 아닌 모든 요청(존재하지 않는 경로 포함)이 {@code
 * none}으로 잡힙니다. 제거 조건 판단에는 안전한 방향입니다. 실제보다 늦게 0에 수렴할 뿐 먼저 수렴하지 않습니다.
 *
 * <p>존재 이유는 전환기 fallback의 <b>제거 조건</b>입니다(ADR-0017). 레거시 {@code session_token}과 신규 {@code
 * access_token}은 같은 서명 키와 같은 claim을 쓰므로 토큰만으로는 구분할 수 없고, 구분할 수 있는 신호는 "어느 쿠키 이름으로 왔는가"뿐입니다. 이 지표가
 * 없으면 "모든 웹 세션이 {@code access_token}을 갖게 됐다"를 확인할 방법이 없어 fallback을 언제 걷어도 되는지 판단할 수 없습니다.
 *
 * <p>태그 키는 {@code mode}입니다. 같은 일(토큰 전달)을 하는 경로의 구분이기 때문입니다(docs/rules/observability.md 태그 표). 값은
 * {@link Mode}의 고정 어휘 4개뿐이라 카디널리티가 4로 닫혀 있고, 토큰 값이나 사용자 식별자는 남기지 않습니다.
 *
 * <p>네 조합이 부팅 시점에 모두 정해지므로 생성자에서 {@code 0}으로 미리 등록합니다. 등록하지 않으면 재시작 후 첫 요청 전까지 시계열이 없어 그 구간이 장애와
 * 구분되지 않습니다(지표 규약의 "0 선등록").
 */
@Component
class BearerTokenResolutionMetrics {

  private static final String RESOLUTIONS = "momens.auth.bearer.token.resolutions";

  /**
   * 토큰이 해석된 경로.
   *
   * <p>{@link #NONE}은 실패가 아니라 "토큰 없이 온 요청"입니다. 401로 끝나는 이 경우를 빼면 태그 집합이 요청마다 달라져 집계가 깨집니다.
   */
  enum Mode {
    HEADER("header"),
    ACCESS_COOKIE("access_cookie"),
    LEGACY_SESSION_COOKIE("legacy_session_cookie"),
    NONE("none");

    private final String value;

    Mode(String value) {
      this.value = value;
    }
  }

  private final Map<Mode, Counter> counters = new EnumMap<>(Mode.class);

  BearerTokenResolutionMetrics(MeterRegistry meterRegistry) {
    for (Mode mode : Mode.values()) {
      counters.put(
          mode, Counter.builder(RESOLUTIONS).tag("mode", mode.value).register(meterRegistry));
    }
  }

  void record(Mode mode) {
    counters.get(mode).increment();
  }
}
