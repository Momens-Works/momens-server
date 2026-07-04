package works.momens.server.user.internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.user.UserErrorCode;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

@Service
@RequiredArgsConstructor
class UserServiceImpl implements UserService {

  private final UserRepository userRepository;

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
