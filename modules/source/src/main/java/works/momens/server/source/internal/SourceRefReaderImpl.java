package works.momens.server.source.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.source.SourceRefReader;
import works.momens.server.source.SourceRefView;

@Service
@RequiredArgsConstructor
class SourceRefReaderImpl implements SourceRefReader {

  private final SourceRefRepository sourceRefRepository;

  @Override
  @Transactional(readOnly = true)
  public List<SourceRefView> findByIds(UUID workspaceId, Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return sourceRefRepository.findByWorkspaceIdAndIdInAndDeletedAtIsNull(workspaceId, ids).stream()
        .map(SourceRefReaderImpl::toView)
        .toList();
  }

  private static SourceRefView toView(SourceRef sourceRef) {
    return new SourceRefView(
        sourceRef.getId(),
        sourceRef.getSourceType(),
        sourceRef.getTitle(),
        sourceRef.getSnippet(),
        sourceRef.getText(),
        sourceRef.getSourceUrl(),
        sourceRef.getSourceCreatedAt());
  }
}
