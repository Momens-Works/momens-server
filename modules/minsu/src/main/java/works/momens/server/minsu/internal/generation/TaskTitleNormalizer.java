package works.momens.server.minsu.internal.generation;

final class TaskTitleNormalizer {

  static final int MAX_LENGTH = 15;

  private TaskTitleNormalizer() {}

  static String normalize(String title) {
    String normalized = title == null ? "" : title.trim();
    if (normalized.length() <= MAX_LENGTH) {
      return normalized;
    }
    int end = MAX_LENGTH;
    if (Character.isHighSurrogate(normalized.charAt(end - 1))
        && Character.isLowSurrogate(normalized.charAt(end))) {
      end--;
    }
    return normalized.substring(0, end).stripTrailing();
  }
}
