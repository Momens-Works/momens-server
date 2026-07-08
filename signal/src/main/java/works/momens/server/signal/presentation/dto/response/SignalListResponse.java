package works.momens.server.signal.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import works.momens.server.signal.internal.SignalSummary;

/**
 * {@code GET /api/mobile/projects/{projectId}/signals} 응답. shape는 docs/spec/mobile-api.md의 Signal
 * 목록 절을 따른다.
 *
 * <p>title·description은 카드 헤더에 쓰이는 고정 문구로, Signal 건별 값이 아니다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Signal 목록 응답")
public record SignalListResponse(
    @Schema(description = "카드 헤더 제목") String title,
    @Schema(description = "카드 헤더 설명") String description,
    @Schema(description = "미처리 Signal 목록(생성 시각 내림차순). 없으면 빈 배열입니다.") List<SignalItem> signals) {

  private static final String TITLE = "오늘 확인해야 할 시그널";
  private static final String DESCRIPTION = "프로젝트의 의사결정에 영향을 줄 수 있는 변화입니다.";

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @Schema(description = "Signal 목록 카드 한 건")
  public record SignalItem(
      @Schema(description = "Signal 식별자") UUID id,
      @Schema(description = "프로젝트 식별자") UUID projectId,
      @Schema(description = "Signal 유형", example = "risk") String type,
      @Schema(description = "제목") String title,
      @Schema(description = "프로젝트에 미칠 영향 요약. 미생산 시 null입니다.", nullable = true) String impact,
      @Schema(description = "민수 제안. 미생산 시 null입니다.", nullable = true) String minsuSuggestion) {}

  public static SignalListResponse from(List<SignalSummary> signals) {
    return new SignalListResponse(
        TITLE,
        DESCRIPTION,
        signals.stream()
            .map(
                signal ->
                    new SignalItem(
                        signal.id(),
                        signal.projectId(),
                        signal.type(),
                        signal.title(),
                        signal.impact(),
                        signal.minsuSuggestion()))
            .toList());
  }
}
