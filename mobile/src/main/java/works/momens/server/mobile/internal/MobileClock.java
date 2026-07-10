package works.momens.server.mobile.internal;

import java.time.Clock;

/**
 * 모바일 조합 서비스가 쓰는 시간 소스. {@link Clock}을 직접 빈으로 노출하면 다른 모듈(auth)의 Clock 주입과 타입이 겹쳐 충돌하므로, 전용 타입으로
 * 감쌉니다. 브리프의 "오늘"은 현재 시각에 의존하므로 테스트에서 고정 Clock으로 교체할 수 있게 합니다.
 */
public class MobileClock {

  private final Clock clock;

  public MobileClock(Clock clock) {
    this.clock = clock;
  }

  public Clock clock() {
    return clock;
  }
}
