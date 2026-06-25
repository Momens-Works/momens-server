package works.momens.server.auth.internal.refresh;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Modifying(flushAutomatically = true)
  @Query(
      """
      UPDATE RefreshToken token
         SET token.revokedAt = :revokedAt
       WHERE token.userId = :userId
         AND token.clientType = :clientType
         AND ((:device IS NULL AND token.device IS NULL) OR token.device = :device)
         AND token.revokedAt IS NULL
         AND token.expiresAt > :revokedAt
      """)
  void revokeActiveBySessionScope(
      @Param("userId") UUID userId,
      @Param("clientType") ClientType clientType,
      @Param("device") String device,
      @Param("revokedAt") Instant revokedAt);
}
