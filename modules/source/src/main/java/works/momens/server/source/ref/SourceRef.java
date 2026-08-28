package works.momens.server.source.ref;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import works.momens.server.common.persistence.BaseEntity;

/**
 * 근거 원본을 가리키는 포인터인 {@code source_ref}입니다.
 *
 * <p>레거시 {@code momens-api}가 소유하는 {@code source_refs} 테이블({@code 000002_retrieval_projection.sql})을
 * 매핑합니다. 운영 환경에서는 공유 DB의 테이블을 {@code ddl-auto=validate}로 검증하고, local과 test 환경에서는 같은 컬럼을 생성해 사용합니다.
 *
 * <p>{@code MOM-0868}에서 사용자가 붙여넣은 링크를 저장하는 경로가 추가되어 {@code @Immutable}을 제거하고 {@link
 * works.momens.server.common.persistence.BaseEntity}를 상속합니다. 읽기 전용으로 선언하면 이후 수정 메서드를 추가해도 UPDATE가
 * 예외 없이 무시될 수 있습니다. 감사 컬럼도 애플리케이션에서 채워야 합니다. 컬럼을 매핑한 상태에서 값을 설정하지 않으면 INSERT 문에 {@code NULL}이 포함되어
 * 제약을 위반합니다.
 *
 * <p>{@code metadata}는 레거시가 빈 객체를 저장하는 컬럼이므로 함께 매핑합니다. 조회 응답에는 포함하지 않습니다.
 */
@Getter
@Entity
@Table(name = "source_refs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SourceRef extends BaseEntity {

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(name = "source_type", nullable = false)
  private String sourceType;

  @Column(name = "source_object_type", nullable = false)
  private String sourceObjectType;

  @Column(name = "source_object_id", nullable = false)
  private String sourceObjectId;

  @Column private String title;

  @Column private String snippet;

  @Column(name = "text")
  private String text;

  @Column(name = "source_url")
  private String sourceUrl;

  @Column(name = "source_created_at")
  private Instant sourceCreatedAt;

  @Column(name = "author_name")
  private String authorName;

  @Column(name = "author_email")
  private String authorEmail;

  @Column(nullable = false)
  private String visibility;

  @Column(name = "permission_key")
  private String permissionKey;

  @Column(name = "verified_by_user_id", columnDefinition = "uuid")
  private UUID verifiedByUserId;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Builder
  private SourceRef(
      UUID workspaceId,
      String sourceType,
      String sourceObjectType,
      String sourceObjectId,
      String title,
      String sourceUrl,
      String visibility,
      Map<String, Object> metadata) {
    this.workspaceId = workspaceId;
    this.sourceType = sourceType;
    this.sourceObjectType = sourceObjectType;
    this.sourceObjectId = sourceObjectId;
    this.title = title;
    this.sourceUrl = sourceUrl;
    this.visibility = visibility;
    this.metadata = metadata;
  }
}
