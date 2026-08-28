package works.momens.server.source;

/** provider가 승인 결과와 함께 반환한 승인 코드와 state를 담습니다. */
public record CompleteInstallCommand(String code, String state) {}
