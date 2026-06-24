package works.momens.server.user;

import java.time.Instant;
import java.util.UUID;

/**
 * 모듈 경계를 넘는 사용자 프로필 표현(읽기 모델).
 *
 * <p>엔티티({@code internal.User})와 분리된 public API 반환 타입입니다. presentation 응답 DTO와도 별개이며, 컨트롤러는 이 값을 응답
 * shape로 매핑합니다.
 */
public record UserProfile(
    UUID id,
    String email,
    String name,
    String jobRole,
    String avatarUrl,
    Instant createdAt,
    Instant updatedAt) {}
