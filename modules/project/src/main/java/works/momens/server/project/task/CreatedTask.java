package works.momens.server.project.task;

import java.util.UUID;

/** 태스크 생성 결과. */
public record CreatedTask(
    UUID id, UUID projectId, String title, String role, String priority, String status) {}
