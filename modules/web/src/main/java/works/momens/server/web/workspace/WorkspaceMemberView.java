package works.momens.server.web.workspace;

import java.time.Instant;
import java.util.UUID;

/**
 * 멤버십과 사용자 정보를 조합한 조회 결과.
 *
 * <p>멤버십은 workspace 모듈이 소유하고, 이름과 이메일은 user 모듈이 소유하므로 두 정보를 이 모듈에서 조합합니다. 응답 형식은 이 타입이 아니라 DTO가
 * 소유하므로 Swagger 애너테이션은 선언하지 않습니다.
 */
public record WorkspaceMemberView(
    UUID userId, String email, String name, String role, Instant createdAt, Instant updatedAt) {}
