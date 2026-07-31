package works.momens.server.signal.action;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SignalActionRepository extends JpaRepository<SignalAction, UUID> {

  Optional<SignalAction> findBySignalId(UUID signalId);
}
