package works.momens.server.signal;

import java.util.List;

/**
 * Signal 요약의 커서 페이지 한 장.
 *
 * <p>{@code nextCursor}는 다음 페이지를 여는 커서 문자열이고, 다음 페이지가 없으면 null입니다. 커서는 이 모듈만 해석하므로 호출하는 쪽은 내용을 해석하지
 * 않고 그대로 되돌려줍니다.
 */
public record SignalSummaryPage(List<SignalSummary> items, String nextCursor) {}
