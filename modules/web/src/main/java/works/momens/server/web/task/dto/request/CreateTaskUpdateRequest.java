package works.momens.server.web.task.dto.request;

import java.util.Map;

public record CreateTaskUpdateRequest(String body, String kind, Map<String, Object> metadata) {}
