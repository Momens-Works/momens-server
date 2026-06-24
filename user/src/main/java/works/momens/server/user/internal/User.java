package works.momens.server.user.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import works.momens.server.common.persistence.BaseEntity;

/**
 * 사용자.
 *
 * <p>레거시 {@code momens-api}의 {@code users} 테이블과 호환됩니다. 식별자·감사 필드는 {@link BaseEntity}가 담당합니다.
 *
 * <p>엔티티가 cross-module API로 노출되지 않도록 모듈 {@code internal} 패키지에 package-private로 둡니다. 다른 모듈은 루트 패키지의
 * {@code UserService}/{@code UserProfile}만 사용합니다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class User extends BaseEntity {

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String name;

  @Column(name = "avatar_url")
  private String avatarUrl;

  @Column(name = "job_role")
  private String jobRole;

  @Builder
  private User(String email, String name, String avatarUrl, String jobRole) {
    this.email = email;
    this.name = name;
    this.avatarUrl = avatarUrl;
    this.jobRole = jobRole;
  }

  /** 로그인 시 검증된 Google 프로필(name/avatar)을 최신값으로 갱신합니다. */
  void refreshGoogleProfile(String name, String avatarUrl) {
    this.name = name;
    this.avatarUrl = avatarUrl;
  }

  /**
   * 프로필 부분 수정. {@code null} 필드는 변경하지 않습니다.
   *
   * <p>레거시는 {@code job_role}을 명시적 null로 비울 수 있었으나(tri-state), v1은 null=무변경으로 단순화합니다.
   */
  void updateProfile(String name, String jobRole) {
    if (name != null) {
      this.name = name;
    }
    if (jobRole != null) {
      this.jobRole = jobRole;
    }
  }
}
