package works.momens.server.mobile;

import java.time.Clock;

/**
 * 모바일 모듈의 시간 소스이자 공개 시간 seam. {@link Clock}을 직접 빈으로 노출하면 다른 모듈(auth)의 Clock 주입과 타입이 겹쳐 충돌하므로, 전용
 * 타입으로 감쌉니다.
 *
 * <p>브리프의 "오늘"은 현재 시각에 의존하므로, 통합 테스트가 모듈 내부를 넘겨보지 않고 이 공개 타입을 고정 Clock으로 덮어써 시각을 결정적으로 만들 수 있게 모듈
 * 공개 API로 둡니다. 배선은 내부({@code internal.MobileTimeConfig})가 소유합니다.
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
