package works.momens.server.mobile.internal;

import java.util.List;

/** 보드 그룹 하나. {@code groupKey}는 todo, in_progress, done 중 하나다. */
public record MobileTaskGroup(String groupKey, List<MobileTaskCard> tasks) {}
