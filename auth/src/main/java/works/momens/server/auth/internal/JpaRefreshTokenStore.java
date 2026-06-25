package works.momens.server.auth.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaRefreshTokenStore implements RefreshTokenStore {

  private final RefreshTokenRepository refreshTokenRepository;

  @Override
  public RefreshToken save(
      UUID userId, String tokenHash, ClientType clientType, String device, Instant expiresAt) {
    return refreshTokenRepository.save(
        new RefreshToken(userId, tokenHash, clientType, device, expiresAt));
  }

  @Override
  public Optional<RefreshToken> findByTokenHash(String tokenHash) {
    return refreshTokenRepository.findByTokenHash(tokenHash);
  }

  @Override
  public void revoke(RefreshToken refreshToken, Instant revokedAt) {
    refreshToken.revoke(revokedAt);
  }

  @Override
  public void revokeActiveBySessionScope(
      UUID userId, ClientType clientType, String device, Instant revokedAt) {
    refreshTokenRepository.revokeActiveBySessionScope(userId, clientType, device, revokedAt);
  }
}
