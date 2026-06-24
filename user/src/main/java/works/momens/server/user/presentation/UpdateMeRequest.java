package works.momens.server.user.presentation;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** 프로필 수정 요청. 부분 수정(PATCH)이라 제공된(non-null) 필드만 갱신합니다. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record UpdateMeRequest(
    @Size(max = 255) @Pattern(regexp = ".*\\S.*") String name, @Size(max = 255) String jobRole) {}
