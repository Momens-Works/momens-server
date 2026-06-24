package works.momens.server.user.presentation;

import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 프로필 수정 요청. 부분 수정(PATCH)이라 두 필드 모두 선택입니다.
 *
 * <p>제공된(non-null) 필드만 갱신합니다. 레거시는 {@code job_role}을 명시적 null로 비울 수 있었으나(tri-state), v1은 null=무변경으로
 * 단순화합니다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record UpdateMeRequest(@Size(max = 255) String name, @Size(max = 255) String jobRole) {}
