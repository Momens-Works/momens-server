package works.momens.server.notification.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PushInstallationRepository extends JpaRepository<PushInstallation, UUID> {

  Optional<PushInstallation> findByFirebaseInstallationId(String firebaseInstallationId);

  List<PushInstallation> findByFcmRegistrationTokenAndActiveTrue(String fcmRegistrationToken);

  List<PushInstallation> findByUserIdInAndPlatformAndActiveTrue(
      Collection<UUID> userIds, String platform);
}
