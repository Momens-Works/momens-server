package works.momens.server.auth.internal.refresh;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * refresh token 저장소 port.
 *
 * <p>초기 구현은 JPA/PostgreSQL이며, Redis 전환 또는 보조 저장소 추가가 필요할 때 이 use case 경계 안에서 구현을 교체합니다.
 */
public interface RefreshTokenStore {

  RefreshToken save(
      UUID userId, String tokenHash, ClientType clientType, String device, Instant expiresAt);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  void revoke(RefreshToken refreshToken, Instant revokedAt);

  void revokeActiveBySessionScope(
      UUID userId, ClientType clientType, String device, Instant revokedAt);
}
