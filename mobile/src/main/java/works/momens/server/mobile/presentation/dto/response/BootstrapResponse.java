package works.momens.server.mobile.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import works.momens.server.mobile.internal.BootstrapContext;

/**
 * {@code GET /api/mobile/bootstrap} 응답. shape는 docs/spec/mobile-api.md의 모바일 진입 절을 따릅니다.
 *
 * <p>신규 Standard 계약이라 {@code avatar_url}과 {@code default_project_id}는 값이 없어도 null로 항상 포함합니다(레거시
 * {@code /me}의 avatar_url 생략과 의도된 차이).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "모바일 앱 진입 컨텍스트 응답")
public record BootstrapResponse(
    @Schema(description = "현재 사용자 요약") MeResponse me,
    @Schema(
            description = "기본으로 보여줄 project id. 가장 최근에 만든 project이며, 접근 가능한 project가 없으면 null입니다.",
            example = "30d9e9fe-f43b-4097-a88e-dc19f0a5b025",
            nullable = true)
        UUID defaultProjectId,
    @Schema(description = "접근 가능한 project 목록(생성 최신순). 없으면 빈 배열입니다.")
        List<AccessibleProjectResponse> projects) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @Schema(description = "현재 사용자 요약")
  public record MeResponse(
      @Schema(description = "사용자 식별자", example = "5d2f7f3a-5db1-4f2c-8b9e-13607dd1f5e8") UUID id,
      @Schema(description = "이름", example = "김민지") String name,
      @Schema(description = "아바타 URL. 미설정이면 null로 포함됩니다.", nullable = true) String avatarUrl) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @Schema(description = "접근 가능한 project 한 건")
  public record AccessibleProjectResponse(
      @Schema(description = "project 식별자", example = "30d9e9fe-f43b-4097-a88e-dc19f0a5b025")
          UUID id,
      @Schema(description = "project 이름", example = "Q2 Activation Readiness") String name,
      @Schema(description = "소속 workspace 멤버십 role", example = "member") String role) {}

  public static BootstrapResponse from(BootstrapContext context) {
    return new BootstrapResponse(
        new MeResponse(context.me().id(), context.me().name(), context.me().avatarUrl()),
        context.defaultProjectId(),
        context.projects().stream()
            .map(
                project ->
                    new AccessibleProjectResponse(project.id(), project.name(), project.role()))
            .toList());
  }
}
