package works.momens.server.mobile.internal;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import works.momens.server.mobile.MobileClock;

/** 모바일 시간 소스 배선. {@link MobileClock}으로 감싸 다른 모듈의 Clock 빈과 타입이 겹치지 않게 합니다. */
@Configuration
class MobileTimeConfig {

  @Bean
  MobileClock mobileClock() {
    return new MobileClock(Clock.systemUTC());
  }
}
