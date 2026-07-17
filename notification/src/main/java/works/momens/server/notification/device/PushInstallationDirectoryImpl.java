package works.momens.server.notification.device;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 설치 원장 조회·무효 token 비활성화 구현. 호출자(delivery) 트랜잭션에 참여한다. */
@Component
@RequiredArgsConstructor
class PushInstallationDirectoryImpl implements PushInstallationDirectory {

  private static final String PLATFORM_ANDROID = "android";

  private final PushInstallationRepository pushInstallationRepository;

  @Override
  @Transactional(readOnly = true)
  public List<InstallationSnapshot> findActiveAndroid(Collection<UUID> userIds) {
    if (userIds.isEmpty()) {
      return List.of();
    }
    return pushInstallationRepository
        .findByUserIdInAndPlatformAndActiveTrue(userIds, PLATFORM_ANDROID)
        .stream()
        .map(PushInstallationDirectoryImpl::toSnapshot)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<InstallationSnapshot> findByIds(Collection<UUID> installationIds) {
    return pushInstallationRepository.findAllById(installationIds).stream()
        .map(PushInstallationDirectoryImpl::toSnapshot)
        .toList();
  }

  @Override
  @Transactional
  public void deactivate(UUID installationId) {
    pushInstallationRepository.findById(installationId).ifPresent(PushInstallation::deactivate);
  }

  private static InstallationSnapshot toSnapshot(PushInstallation installation) {
    return new InstallationSnapshot(
        installation.getId(),
        installation.getUserId(),
        installation.getFcmRegistrationToken(),
        installation.isActive());
  }
}
