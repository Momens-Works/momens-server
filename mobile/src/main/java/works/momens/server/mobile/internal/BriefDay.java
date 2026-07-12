package works.momens.server.mobile.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 브리프의 "오늘" 하루 경계를 계산하는 단일 출처. 오늘의 브리프는 그날 생성된 시그널을 보여주므로, 어떤 타임존의 하루로 볼지를 여기서 정합니다.
 *
 * <p>MVP는 한국 사용자를 대상으로 하므로 하루 경계를 Asia/Seoul 기준으로 둡니다(2026-07-10 확정). KST는 서머타임이 없어 경계가 단순합니다. 이후
 * 프로젝트별이나 사용자별 타임존이 필요해지면 이 상수와 계산만 바꾸면 됩니다.
 */
final class BriefDay {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  private BriefDay() {}

  /** 시그널 created_at을 거를 조회 범위. 끝은 포함하지 않습니다. */
  record Range(Instant from, Instant toExclusive) {}

  /** 지금 시각 기준 오늘(KST)의 날짜입니다. 페이지 세션의 기준일 앵커로 커서에 실어 창을 고정합니다. */
  static LocalDate today(Clock clock) {
    return LocalDate.now(clock.withZone(ZONE));
  }

  /** 주어진 날짜(KST)의 시작과 다음 날 0시를 UTC Instant 범위로 계산합니다. */
  static Range rangeOf(LocalDate date) {
    Instant from = date.atStartOfDay(ZONE).toInstant();
    Instant toExclusive = date.plusDays(1).atStartOfDay(ZONE).toInstant();
    return new Range(from, toExclusive);
  }
}
