package works.momens.server.user.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import works.momens.server.user.UserProfile;

/**
 * {@code /me} 응답의 user 객체. 레거시 {@code momens-api}의 user JSON shape를 보존합니다.
 *
 * <p>필드 순서·이름(snake_case)은 레거시와 동일하며, {@code avatar_url}은 미설정 시 생략, {@code job_role}은 null이어도 항상
 * 포함합니다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record UserResponse(
    UUID id,
    String email,
    String name,
    @JsonInclude(JsonInclude.Include.NON_NULL) String avatarUrl,
    String jobRole,
    Instant createdAt,
    Instant updatedAt) {

  static UserResponse from(UserProfile p) {
    return new UserResponse(
        p.id(), p.email(), p.name(), p.avatarUrl(), p.jobRole(), p.createdAt(), p.updatedAt());
  }
}
