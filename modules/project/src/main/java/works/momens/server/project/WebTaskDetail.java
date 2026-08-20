package works.momens.server.project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 웹 Product API task 응답에 필요한 저장값 투영입니다. */
public record WebTaskDetail(
    UUID id,
    UUID workspaceId,
    UUID projectId,
    UUID milestoneId,
    String label,
    String title,
    String description,
    String status,
    String priority,
    UUID assigneeId,
    LocalDate dueDate,
    Instant createdAt,
    Instant updatedAt) {}
