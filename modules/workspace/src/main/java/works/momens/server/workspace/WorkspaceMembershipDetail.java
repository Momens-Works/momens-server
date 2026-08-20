package works.momens.server.workspace;

import java.time.Instant;
import java.util.UUID;

/**
 * workspace 멤버십 한 건의 상세 조회 결과.
 *
 * <p>{@link WorkspaceMembership}과 같은 멤버십을 나타내지만 제공하는 필드가 다릅니다. {@code WorkspaceMembership}은 userId와
 * role만 필요한 소비자가 사용하고, 이 타입은 멤버 목록 응답처럼 멤버가 된 시각까지 필요한 소비자가 사용합니다. 사용하지 않는 필드를 기존 타입에 추가하지 않도록 별도
 * 타입으로 분리했습니다.
 *
 * <p>이름과 이메일 등 사용자 정보는 user 모듈이 소유하므로 포함하지 않습니다. 멤버십과 사용자 정보를 조합하는 책임은 호출하는 쪽에 있습니다.
 */
public record WorkspaceMembershipDetail(
    UUID userId, String role, Instant createdAt, Instant updatedAt) {}
