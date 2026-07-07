package works.momens.server.signal.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SignalRepository extends JpaRepository<Signal, UUID> {

  Optional<Signal> findByIdAndDeletedAtIsNull(UUID id);
}
