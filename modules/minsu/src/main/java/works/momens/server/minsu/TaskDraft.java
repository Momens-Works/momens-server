package works.momens.server.minsu;

/** 검증과 title 정규화가 끝난 Signal task draft. */
public record TaskDraft(String title, Role role, Priority priority) {}
