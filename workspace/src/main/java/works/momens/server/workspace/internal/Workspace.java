package works.momens.server.workspace.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import works.momens.server.common.persistence.BaseEntity;

/**
 * 워크스페이스.
 *
 * <p>레거시 {@code momens-api}의 {@code workspaces} 테이블과 호환됩니다. 식별자와 감사 필드는 {@link BaseEntity}가 담당합니다.
 *
 * <p>엔티티가 cross-module API로 노출되지 않도록 모듈 {@code internal} 패키지에 package-private로 둡니다. 다른 모듈은
 * workspace 모듈의 public API만 사용합니다.
 */
@Getter
@Entity
@Table(name = "workspaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Workspace extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String slug;

  @Column private String description;

  @Builder
  private Workspace(String name, String slug, String description) {
    this.name = name;
    this.slug = slug;
    this.description = description;
  }
}
