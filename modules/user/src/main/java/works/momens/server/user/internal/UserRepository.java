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
  @Modifying(flushAutomatically = true)
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

  /**
   * 다른 사용자가 사용 중이지 않은 경우에만 이메일을 갱신합니다.
   *
   * <p>갱신된 행 수를 반환하며, 0이면 이메일 갱신이 수행되지 않은 것입니다.
   *
   * <p>충돌하는 이메일을 그대로 갱신하면 제약 조건 위반으로 트랜잭션이 중단되어 로그인 자체가 실패할 수 있습니다. 이를 방지하기 위해 UPDATE 조건에서 이메일 충돌을
   * 사전에 제외합니다(ADR-0016).
   *
   * <p>갱신 이후에는 1차 캐시에 남아 있는 값이 DB와 달라질 수 있으므로 {@code clearAutomatically}로 영속성 컨텍스트를 초기화합니다.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE users
          SET email = :email,
              updated_at = NOW()
          WHERE id = :id
            AND NOT EXISTS (
                SELECT 1
                FROM users u2
                WHERE u2.email = :email
                  AND u2.id <> :id
            )
          """,
      nativeQuery = true)
  int updateEmailIfUnused(@Param("id") UUID id, @Param("email") String email);
}
