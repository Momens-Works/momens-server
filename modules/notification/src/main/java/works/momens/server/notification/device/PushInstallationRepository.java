package works.momens.server.notification.device;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PushInstallationRepository extends JpaRepository<PushInstallation, UUID> {

  Optional<PushInstallation> findByFirebaseInstallationId(String firebaseInstallationId);

  List<PushInstallation> findByFcmRegistrationTokenAndActiveTrue(String fcmRegistrationToken);

  List<PushInstallation> findByUserIdInAndPlatformAndActiveTrue(
      Collection<UUID> userIds, String platform);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "UPDATE push_installations SET active = false, deactivated_at = NOW(), "
              + "updated_at = NOW() WHERE id = :installationId "
              + "AND fcm_registration_token = :claimedToken AND active = true",
      nativeQuery = true)
  int deactivateIfTokenMatches(
      @Param("installationId") UUID installationId, @Param("claimedToken") String claimedToken);
}
