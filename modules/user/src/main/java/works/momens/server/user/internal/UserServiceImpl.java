package works.momens.server.user.internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.user.UserErrorCode;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

@Service
@RequiredArgsConstructor
@Slf4j
class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final UserIdentityRepository userIdentityRepository;

  @Override
  @Transactional
  public UserProfile findOrCreateByIdentity(
      String provider, String providerUserId, String email, String name, String avatarUrl) {
    Optional<UserIdentity> identity =
        userIdentityRepository.findByProviderAndProviderUserId(provider, providerUserId);
    if (identity.isPresent()) {
      return refreshProfile(identity.get().getUserId(), email, name, avatarUrl);
    }

    Optional<User> byEmail = userRepository.findByEmail(email);
    if (byEmail.isPresent()) {
      UUID userId = byEmail.get().getId();
      // 이메일이 다른 계정으로 재할당된 경우, 이메일 조회 시 이전 사용자의 행이 반환될 수 있다.
      // 해당 사용자에게 이미 다른 로그인 수단이 연결되어 있다면
      // 다른 사람의 계정에 로그인 수단을 연결하는 상황이 되므로 요청을 거부한다.
      // 이관 기간에는 email UNIQUE 제약이 유지되므로 동일 이메일로 신규 사용자 생성도 불가능하다.
      if (userIdentityRepository.existsOtherIdentity(userId, provider, providerUserId)) {
        throw new BusinessException(UserErrorCode.USER_EMAIL_LINKED_TO_ANOTHER_IDENTITY);
      }
      // 삽입 결과를 별도로 확인하지 않아도 되는 이유는 users.email에 UNIQUE 제약이 있기 때문이다.
      // 동일한 이메일로 들어온 요청은 모두 하나의 users 행으로 수렴하므로,
      // 경합에서 실패한 요청도 결국 같은 사용자로 연결된다.
      // 이관이 완료되어 해당 제약을 제거하게 되면 이 전제가 더 이상 성립하지 않으므로,
      // 이 코드 역시 함께 검토해야 한다(ADR-0016 결정 6).
      userIdentityRepository.insertIgnoringConflict(
          UUID.randomUUID(), userId, provider, providerUserId);
      return refreshProfile(userId, email, name, avatarUrl);
    }

    // 동일한 이메일로 최초 로그인 요청이 동시에 들어오더라도 upsert를 통해 하나의 users 행으로 수렴한다.
    // 이후 로그인 수단 삽입 과정에서 경쟁에 진 요청은 0건을 반환한다.
    // 경쟁에서 승리한 요청도 동일한 users 행에 로그인 수단을 연결하므로,
    // 이후 조회 시 두 요청 모두 동일한 사용자를 반환한다.
    userRepository.upsertByEmail(UUID.randomUUID(), email, name, avatarUrl);
    User created = userRepository.findByEmail(email).orElseThrow();
    int inserted =
        userIdentityRepository.insertIgnoringConflict(
            UUID.randomUUID(), created.getId(), provider, providerUserId);
    if (inserted == 0) {
      UUID winner =
          userIdentityRepository
              .findByProviderAndProviderUserId(provider, providerUserId)
              .orElseThrow()
              .getUserId();
      return refreshProfile(winner, email, name, avatarUrl);
    }
    return toProfile(created);
  }

  /** 로그인 시점의 최신 프로필을 반영합니다. 이메일은 충돌 시 건너뛰므로 별도 쿼리로 처리합니다. */
  private UserProfile refreshProfile(UUID userId, String email, String name, String avatarUrl) {
    User user = userRepository.findById(userId).orElseThrow(() -> notFound(userId));
    user.refreshLoginProfile(name, avatarUrl);
    if (userRepository.updateEmailIfUnused(userId, email) == 0) {
      // 개인정보(email)는 로그에 남기지 않으며, 식별은 userId로만 수행한다. (code-conventions 규칙)
      log.warn("로그인 이메일을 갱신하지 않았습니다. 해당 이메일은 이미 다른 사용자가 사용 중입니다. userId={}", userId);
    }
    return toProfile(userRepository.findById(userId).orElseThrow());
  }

  @Override
  @Transactional
  public UserProfile findOrCreate(String email, String name, String avatarUrl) {
    // email 기준 원자적 upsert. 같은 신규 email로 동시 최초 로그인이 들어와도 unique 충돌 500 없이 한 행으로 수렴한다
    // (read-then-insert의 경합 회피). id는 신규 행에만 쓰이고 충돌 시 기존 행을 유지한다.
    userRepository.upsertByEmail(UUID.randomUUID(), email, name, avatarUrl);
    return toProfile(userRepository.findByEmail(email).orElseThrow());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserProfile> findByEmail(String email) {
    return userRepository.findByEmail(email).map(UserServiceImpl::toProfile);
  }

  @Override
  @Transactional(readOnly = true)
  public UserProfile getProfile(UUID userId) {
    return userRepository
        .findById(userId)
        .map(UserServiceImpl::toProfile)
        .orElseThrow(() -> notFound(userId));
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserProfile> getProfiles(Collection<UUID> userIds) {
    return userRepository.findAllById(userIds).stream().map(UserServiceImpl::toProfile).toList();
  }

  @Override
  @Transactional
  public UserProfile updateProfile(UUID userId, String name, String jobRole) {
    User user = userRepository.findById(userId).orElseThrow(() -> notFound(userId));
    user.updateProfile(name, jobRole);
    return toProfile(user);
  }

  private static BusinessException notFound(UUID userId) {
    return new BusinessException(
        UserErrorCode.USER_NOT_FOUND, Map.of("user_id", userId.toString()));
  }

  private static UserProfile toProfile(User user) {
    return new UserProfile(
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.getJobRole(),
        user.getAvatarUrl(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
