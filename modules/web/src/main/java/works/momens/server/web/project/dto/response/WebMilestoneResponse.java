package works.momens.server.web.project.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import works.momens.server.project.MilestoneDetail;

@Schema(description = "웹 마일스톤 응답입니다. 필드 구성은 레거시 마일스톤 응답과 동일합니다.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebMilestoneResponse(
    UUID id,
    UUID projectId,
    String name,
    String description,
    LocalDate targetDate,
    String status,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<UUID> ownerUserIds,
    String healthStatus,
    int progress,
    String summary,
    Instant lastContextAt,
    Instant createdAt,
    Instant updatedAt) {

  public static WebMilestoneResponse from(MilestoneDetail milestone) {
    return new WebMilestoneResponse(
        milestone.id(),
        milestone.projectId(),
        milestone.name(),
        milestone.description(),
        milestone.targetDate(),
        milestone.status(),
        milestone.ownerUserIds(),
        milestone.healthStatus(),
        milestone.progress(),
        milestone.summary(),
        milestone.lastContextAt(),
        milestone.createdAt(),
        milestone.updatedAt());
  }
}
