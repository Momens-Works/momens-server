package works.momens.server.web.task.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record CreateWebTaskRequest(
    String title,
    String description,
    String status,
    UUID milestoneId,
    String priority,
    UUID assigneeId,
    LocalDate dueDate) {}
