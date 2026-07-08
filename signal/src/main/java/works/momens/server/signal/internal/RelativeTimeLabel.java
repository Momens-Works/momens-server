package works.momens.server.signal.internal;

import java.time.Duration;
import java.time.Instant;

/**
 * 근거 발생 시각의 상대 표시 라벨.
 *
 * <p>occurred_at을 화면용 한국어 상대 시각으로 바꿉니다(방금 전/N분 전/N시간 전/N일 전). 원천 시각이 없으면(worker 미설정) null입니다. 미래
 * 시각은 시계 오차로 보고 "방금 전"으로 처리합니다.
 */
final class RelativeTimeLabel {

  private RelativeTimeLabel() {}

  static String of(Instant now, Instant occurredAt) {
    if (occurredAt == null) {
      return null;
    }
    long minutes = Duration.between(occurredAt, now).toMinutes();
    if (minutes < 1) {
      return "방금 전";
    }
    if (minutes < 60) {
      return minutes + "분 전";
    }
    long hours = minutes / 60;
    if (hours < 24) {
      return hours + "시간 전";
    }
    return hours / 24 + "일 전";
  }
}
