package works.momens.server.user.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 로그인 수단 조회 리포지토리.
 *
 * <p>{@code UNIQUE (provider, provider_user_id)} 제약이 있으므로 조회 결과는 없거나 하나입니다.
 */
interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

  Optional<UserIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);
}
