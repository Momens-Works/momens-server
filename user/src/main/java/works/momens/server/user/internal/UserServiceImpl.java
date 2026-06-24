package works.momens.server.user.internal;

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
    User user =
        userRepository
            .findByEmail(email)
            .map(
                existing -> {
                  existing.refreshGoogleProfile(name, avatarUrl);
                  return existing;
                })
            .orElseGet(
                () ->
                    userRepository.save(
                        User.builder().email(email).name(name).avatarUrl(avatarUrl).build()));
    return toProfile(user);
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
