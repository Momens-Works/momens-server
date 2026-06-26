package works.momens.server.auth.internal.refresh;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import works.momens.server.common.persistence.BaseEntity;

/** 서버 저장형 refresh token 세션 원장(ADR-0005). 원문 token은 저장하지 않고 해시만 저장합니다. */
@Getter
@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

  @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
  private UUID userId;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "client_type", nullable = false, length = 32)
  private ClientType clientType;

  @Column(length = 255)
  private String device;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Builder
  private RefreshToken(
      UUID userId, String tokenHash, ClientType clientType, String device, Instant expiresAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.clientType = clientType;
    this.device = device;
    this.expiresAt = expiresAt;
  }

  public boolean isActive(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  public void revoke(Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }
}
