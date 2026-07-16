package works.momens.server.mobile.brief;

import java.util.List;

/** 브리프 시그널 요약의 커서 페이지 한 장(필터 전환과 더보기 조회 결과). */
public record MobileBriefSignalPage(List<MobileBrief.SignalItem> items, String nextCursor) {}
