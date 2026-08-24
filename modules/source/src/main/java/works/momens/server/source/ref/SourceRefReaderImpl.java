package works.momens.server.source.ref;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.source.LegacySourceRefDetail;
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
    return sourceRefRepository
        .findByWorkspaceIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId, ids)
        .stream()
        .map(SourceRefReaderImpl::toView)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<LegacySourceRefDetail> findLegacyDetailsByIds(
      UUID workspaceId, Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return sourceRefRepository
        .findByWorkspaceIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId, ids)
        .stream()
        .map(SourceRefReaderImpl::toLegacyDetail)
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

  private static LegacySourceRefDetail toLegacyDetail(SourceRef sourceRef) {
    return new LegacySourceRefDetail(
        sourceRef.getId(),
        sourceRef.getWorkspaceId(),
        sourceRef.getSourceType(),
        sourceRef.getSourceObjectType(),
        sourceRef.getSourceObjectId(),
        sourceRef.getSourceUrl(),
        sourceRef.getTitle(),
        sourceRef.getSnippet(),
        sourceRef.getAuthorName(),
        sourceRef.getAuthorEmail(),
        sourceRef.getSourceCreatedAt(),
        sourceRef.getVisibility(),
        sourceRef.getPermissionKey(),
        sourceRef.getVerifiedByUserId(),
        sourceRef.getVerifiedAt(),
        sourceRef.getCreatedAt(),
        sourceRef.getUpdatedAt());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> findWorkspaceId(UUID sourceRefId) {
    return sourceRefRepository.findWorkspaceId(sourceRefId);
  }
}
