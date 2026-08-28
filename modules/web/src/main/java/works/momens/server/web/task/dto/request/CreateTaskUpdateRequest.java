package works.momens.server.web.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "웹 태스크 업데이트 생성 요청")
public record CreateTaskUpdateRequest(
    @Schema(description = "업데이트 본문", requiredMode = Schema.RequiredMode.REQUIRED) String body,
    @Schema(
            description = "업데이트 종류. 생략하거나 빈 값이면 comment입니다.",
            allowableValues = {"comment", "update"})
        String kind,
    @Schema(description = "추가 메타데이터") Map<String, Object> metadata) {}
