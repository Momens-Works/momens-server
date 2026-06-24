package works.momens.server.user.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  /**
   * email 기준 원자적 upsert. 신규면 INSERT, 이미 있으면 name/avatar를 최신값으로 갱신합니다. {@code id}는 신규 행에만 쓰이고 충돌 시
   * 기존 행을 유지합니다. 감사 필드는 스키마 기본값(created_at {@code DEFAULT now()})과 갱신 시 {@code now()}로 채웁니다.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO users (id, email, name, avatar_url)
          VALUES (:id, :email, :name, :avatarUrl)
          ON CONFLICT (email) DO UPDATE
            SET name = EXCLUDED.name,
                avatar_url = EXCLUDED.avatar_url,
                updated_at = now()
          """,
      nativeQuery = true)
  void upsertByEmail(
      @Param("id") UUID id,
      @Param("email") String email,
      @Param("name") String name,
      @Param("avatarUrl") String avatarUrl);
}
