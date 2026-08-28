package works.momens.server.user.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import works.momens.server.common.persistence.BaseEntity;

/**
 * 사용자의 로그인 수단.
 *
 * <p>한 사용자가 여러 로그인 수단을 가질 수 있으므로 {@code users} 테이블의 컬럼으로 두지 않고 별도 테이블로 분리해 관리합니다.
 *
 * <p>부모 엔티티를 참조하는 {@code @ManyToOne} 매핑은 두지 않고 {@code user_id}만 저장합니다. {@code RefreshToken}도 같은
 * 방식으로 사용자 ID를 관리하며, FK는 마이그레이션에서 정의합니다.
 *
 * <p>{@code @Enumerated(EnumType.STRING)}은 enum 이름을 대문자로 저장하므로 DB에 저장되는 {@code 'google'}과 일치하지
 * 않습니다. 따라서 provider는 문자열로 저장하고, 허용할 값의 범위는 마이그레이션의 CHECK 제약으로 관리합니다.
 *
 * <p>운영 경로에서는 이 엔티티로 직접 행을 저장하지 않습니다. 쓰기 작업은 {@code UserIdentityRepository.insertIgnoringConflict}의
 * 네이티브 INSERT로 처리하며, 아래 생성자는 테스트에서 스키마 제약을 검증할 때 사용합니다. 네이티브 INSERT는 JPA 감사 기능을 거치지 않기 때문에 {@code
 * created_at}과 {@code updated_at}은 스키마에 정의된 기본값으로 채워집니다.
 */
@Getter
@Entity
@Table(name = "user_identities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class UserIdentity extends BaseEntity {

  @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
  private UUID userId;

  @Column(nullable = false)
  private String provider;

  @Column(name = "provider_user_id", nullable = false)
  private String providerUserId;

  @Builder
  private UserIdentity(UUID userId, String provider, String providerUserId) {
    this.userId = userId;
    this.provider = provider;
    this.providerUserId = providerUserId;
  }
}
