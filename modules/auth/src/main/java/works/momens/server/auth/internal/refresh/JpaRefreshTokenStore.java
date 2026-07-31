package works.momens.server.auth.internal.refresh;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class JpaRefreshTokenStore implements RefreshTokenStore {

  private final RefreshTokenRepository refreshTokenRepository;

  @Override
  public RefreshToken save(
      UUID userId, String tokenHash, ClientType clientType, String device, Instant expiresAt) {
    return refreshTokenRepository.save(
        RefreshToken.builder()
            .userId(userId)
            .tokenHash(tokenHash)
            .clientType(clientType)
            .device(device)
            .expiresAt(expiresAt)
            .build());
  }

  @Override
  public Optional<RefreshToken> findByTokenHash(String tokenHash) {
    return refreshTokenRepository.findByTokenHash(tokenHash);
  }

  @Override
  public void revoke(RefreshToken refreshToken, Instant revokedAt) {
    refreshToken.revoke(revokedAt);
  }

  /**
   * 재사용 감지 시 호출됩니다. 호출자(refresh 회전)는 직후 {@code BusinessException}을 던져 자기 트랜잭션을 롤백하므로, 세션 폐기는 별도
   * 트랜잭션(REQUIRES_NEW)에서 커밋해 롤백에 휩쓸리지 않게 합니다.
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void revokeActiveBySessionScope(
      UUID userId, ClientType clientType, String device, Instant revokedAt) {
    refreshTokenRepository.revokeActiveBySessionScope(userId, clientType, device, revokedAt);
  }
}
