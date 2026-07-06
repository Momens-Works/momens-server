package works.momens.server.project;

import java.util.List;
import java.util.UUID;

/** 태스크 생성 결과. {@code roles}는 결정적 순서를 위해 정렬해 담습니다. */
public record CreatedTask(
    UUID id, UUID projectId, String title, List<String> roles, String priority, String status) {}
