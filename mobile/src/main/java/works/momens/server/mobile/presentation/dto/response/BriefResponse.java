package works.momens.server.mobile.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import works.momens.server.project.ProjectSnapshot;

/**
 * {@code GET /api/mobile/projects/{projectId}/brief} 응답. 응답 형식은 docs/spec/mobile-api.md 브리프 절을
 * 따릅니다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "프로젝트 브리프 응답")
public record BriefResponse(@Schema(description = "프로젝트 스냅샷") ProjectResponse project) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @Schema(description = "프로젝트 스냅샷")
  public record ProjectResponse(
      @Schema(description = "project 식별자") UUID id,
      @Schema(description = "이름", example = "Q2 Activation Readiness") String name,
      @Schema(description = "목표일. 미설정이면 null로 포함됩니다.", nullable = true) LocalDate targetDate,
      @Schema(description = "진행률(0~100 정수 퍼센트)", example = "64") int progress,
      @Schema(description = "핵심 목표 요약. 작성 전이면 null로 포함됩니다.", nullable = true) String summary) {}

  public static BriefResponse from(ProjectSnapshot snapshot) {
    return new BriefResponse(
        new ProjectResponse(
            snapshot.id(),
            snapshot.name(),
            snapshot.targetDate(),
            snapshot.progress(),
            snapshot.summary()));
  }
}
