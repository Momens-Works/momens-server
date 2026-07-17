package works.momens.server.notification.device;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * delivery nested 모듈이 설치 원장을 읽고 무효 token 설치를 닫을 때 쓰는 계약.
 *
 * <p>delivery가 설치 엔티티·리포지토리를 직접 만지지 않게 하는 nested 모듈 간 경계다. 설치 lifecycle의 쓰기 정책(등록·소유권 이전·해제)은 여전히
 * {@link works.momens.server.notification.PushDeviceRegistrar}가 소유하고, 이 계약은 발송에 필요한 최소 조회와 무효 token
 * 비활성화만 연다.
 */
public interface PushInstallationDirectory {

  /** 사용자들의 활성 Android 설치를 조회한다(수신자 결정, 설계 9절). */
  List<InstallationSnapshot> findActiveAndroid(Collection<UUID> userIds);

  /** 설치 id로 현재 상태를 조회한다(전송 직전 활성·소유 사용자 재확인, 설계 10.2절). 없는 id는 결과에서 빠진다. */
  List<InstallationSnapshot> findByIds(Collection<UUID> installationIds);

  /** 무효·만료 token의 설치를 발송 대상에서 제외한다(설계 10.3절). 이미 비활성이면 아무것도 바꾸지 않는다. */
  void deactivate(UUID installationId);

  /** 발송이 필요로 하는 설치 최소 스냅샷. */
  record InstallationSnapshot(UUID id, UUID userId, String fcmRegistrationToken, boolean active) {}
}
