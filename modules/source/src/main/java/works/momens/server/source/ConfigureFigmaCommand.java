package works.momens.server.source;

import java.util.List;

/** Figma 팀 webhook과 worker의 파일 허용 목록을 설정합니다. */
public record ConfigureFigmaCommand(String teamId, List<String> fileKeys) {}
