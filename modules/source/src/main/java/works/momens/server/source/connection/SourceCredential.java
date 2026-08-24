package works.momens.server.source.connection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * source 연결 한 건에 대한 provider 접근 토큰을 보관합니다.
 *
 * <p>레거시 {@code momens-api}가 소유하는 {@code source_credentials} 테이블과 호환됩니다. {@code connection_id}가 기본
 * 키이므로 자체 UUID 기본 키를 전제로 하는 {@code BaseEntity}를 상속할 수 없습니다. 같은 구조인 {@code :workspace} 모듈의 {@code
 * WorkspaceMember}처럼 감사 필드를 직접 선언하고 {@link Persistable}을 구현합니다. 감사 필드가 비어 있으면 새 엔티티로 판정합니다.
 *
 * <p>토큰은 {@code TokenEncryptor}가 암호화한 바이트 배열을 저장합니다. 이 엔티티는 평문 토큰을 다루지 않습니다.
 */
@Getter
@Entity
@Table(name = "source_credentials")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceCredential implements Persistable<UUID> {

  @Id
  @Column(name = "connection_id", columnDefinition = "uuid")
  private UUID connectionId;

  @Column(name = "access_token_enc", nullable = false)
  private byte[] accessTokenEnc;

  @Column(name = "refresh_token_enc")
  private byte[] refreshTokenEnc;

  @Column(name = "token_type")
  private String tokenType;

  @Column private String scope;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Builder
  SourceCredential(
      UUID connectionId,
      byte[] accessTokenEnc,
      byte[] refreshTokenEnc,
      String tokenType,
      String scope,
      Instant expiresAt) {
    this.connectionId = connectionId;
    this.accessTokenEnc = accessTokenEnc;
    this.refreshTokenEnc = refreshTokenEnc;
    this.tokenType = tokenType;
    this.scope = scope;
    this.expiresAt = expiresAt;
  }

  /**
   * 기존 연결이 다시 승인되면 저장된 토큰을 새 값으로 교체합니다.
   *
   * <p>provider가 승인할 때마다 새 토큰을 발급하므로 기존 값은 유지하지 않습니다.
   */
  public void replaceTokens(
      byte[] accessTokenEnc,
      byte[] refreshTokenEnc,
      String tokenType,
      String scope,
      Instant expiresAt) {
    this.accessTokenEnc = accessTokenEnc;
    this.refreshTokenEnc = refreshTokenEnc;
    this.tokenType = tokenType;
    this.scope = scope;
    this.expiresAt = expiresAt;
  }

  @Override
  public UUID getId() {
    return connectionId;
  }

  @Override
  public boolean isNew() {
    return createdAt == null;
  }
}
