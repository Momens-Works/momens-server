package works.momens.server.source.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceRefRepository extends JpaRepository<SourceRef, UUID> {

  List<SourceRef> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
