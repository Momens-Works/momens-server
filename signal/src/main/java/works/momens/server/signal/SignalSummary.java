package works.momens.server.signal;

import java.util.UUID;

/** Signal 목록 카드 한 건. impact·minsuSuggestion은 worker/Minsu가 아직 생산하지 않았으면 null일 수 있다. */
public record SignalSummary(
    UUID id, UUID projectId, String type, String title, String impact, String minsuSuggestion) {}
