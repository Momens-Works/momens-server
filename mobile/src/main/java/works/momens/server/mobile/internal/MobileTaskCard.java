package works.momens.server.mobile.internal;

import java.util.UUID;

/** 보드 카드 한 장. {@code priority}는 모바일 응답용으로 urgent를 high로 바꾼 값이다. */
public record MobileTaskCard(
    UUID id, String title, String role, String priority, int materialCount) {}
