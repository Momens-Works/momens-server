package works.momens.server.user.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 프로필 수정 요청. 부분 수정(PATCH)이라 제공된(non-null) 필드만 갱신합니다. */
@Schema(description = "프로필 수정 요청. 제공된(non-null) 필드만 갱신합니다.")
public record UpdateMeRequest(
    @Schema(
            description = "이름. 공백만으로는 설정할 수 없으며 최대 255자입니다.",
            example = "홍길동",
            maxLength = 255,
            nullable = true)
        @Size(max = 255)
        @Pattern(regexp = "(?U).*\\S.*")
        String name,
    @JsonProperty("job_role")
        @Schema(
            description = "직무. 최대 255자입니다.",
            example = "Engineer",
            maxLength = 255,
            nullable = true)
        @Size(max = 255)
        String jobRole) {}
