package works.momens.server.memory.internal;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.context.EntityRelationCommand;
import works.momens.server.context.EntityRelationWriter;
import works.momens.server.memory.ConfirmedMemoryDetail;
import works.momens.server.memory.MemoryErrorCode;
import works.momens.server.memory.MemoryWriter;
import works.momens.server.outbox.OutboxAppender;
import works.momens.server.workspace.LabelAllocator;
import works.momens.server.workspace.WorkspaceAccess;

@Service
@RequiredArgsConstructor
class MemoryWriterImpl implements MemoryWriter {

  private static final String PROPOSED = "PROPOSED";
  private static final String EVENT_CONFIRMED = "memory.confirmed";
  private static final String EVENT_UPDATED = "memory.updated";
  private static final String EVENT_INVALIDATED = "memory.invalidated";
  private static final String EVENT_DELETED = "memory.deleted";

  private final EntityManager entityManager;
  private final MemoryCandidateRepository candidateRepository;
  private final ConfirmedMemoryRepository memoryRepository;
  private final WorkspaceAccess workspaceAccess;
  private final LabelAllocator labelAllocator;
  private final EntityRelationWriter relationWriter;
  private final OutboxAppender outboxAppender;

  @Override
  @Transactional
  public ConfirmedMemoryDetail confirm(UUID candidateId, UUID userId, CandidateEdits edits) {
    MemoryCandidateSnapshot candidate = lockCandidate(candidateId, userId);
    String label = labelAllocator.allocateMemoryLabel(candidate.workspaceId());
    UUID memoryId = UUID.randomUUID();
    Instant now = Instant.now();
    String title =
        edits != null && edits.title() != null && !edits.title().isEmpty()
            ? edits.title()
            : candidate.title();
    String summary =
        edits != null && edits.summary() != null && !edits.summary().isEmpty()
            ? edits.summary()
            : candidate.summary();
    String body =
        edits != null && edits.body() != null && !edits.body().isEmpty()
            ? edits.body()
            : candidate.body();
    entityManager
        .createNativeQuery(
            "UPDATE memory_candidates SET status='CONFIRMED', reviewed_at=:now, reviewed_by_user_id=:userId, updated_at=:now WHERE id=:id")
        .setParameter("now", now)
        .setParameter("userId", userId)
        .setParameter("id", candidateId)
        .executeUpdate();
    entityManager
        .createNativeQuery(
            "INSERT INTO confirmed_memories (id, workspace_id, label, memory_type, title, summary, body, status, source_ref_ids, related_entity_ids, created_from_candidate_id, confirmed_by_user_id, confirmed_at, metadata, created_at, updated_at) SELECT :memoryId, workspace_id, :label, candidate_type, :title, :summary, :body, 'ACTIVE', source_ref_ids, related_entity_ids, id, :userId, :now, metadata, :now, :now FROM memory_candidates WHERE id=:candidateId")
        .setParameter("memoryId", memoryId)
        .setParameter("label", label)
        .setParameter("title", title)
        .setParameter("summary", summary)
        .setParameter("body", body)
        .setParameter("userId", userId)
        .setParameter("now", now)
        .setParameter("candidateId", candidateId)
        .executeUpdate();
    insertReview(
        candidate.workspaceId(),
        candidateId,
        edits == null ? "CONFIRM" : "EDIT_AND_CONFIRM",
        userId,
        edits,
        null,
        null);
    outboxAppender.append(
        candidate.workspaceId(),
        "memory",
        memoryId.toString(),
        EVENT_CONFIRMED,
        Map.of("candidate_id", candidateId.toString()));
    return memoryRepository.findDetailById(memoryId).orElseThrow();
  }

  @Override
  @Transactional
  public ConfirmedMemoryDetail editAndConfirm(UUID candidateId, UUID userId, CandidateEdits edits) {
    return confirm(candidateId, userId, edits);
  }

  @Override
  @Transactional
  public void resolve(UUID resolvedMemoryId, UUID resolvingMemoryId, UUID userId) {
    MemoryReference resolved = findMemoryReference(resolvedMemoryId);
    MemoryReference resolving = findMemoryReference(resolvingMemoryId);
    if (!resolved.workspaceId().equals(resolving.workspaceId())) {
      throw new BusinessException(CommonErrorCode.AUTH_FORBIDDEN);
    }
    if (!workspaceAccess.isMember(resolved.workspaceId(), userId)) {
      throw new BusinessException(CommonErrorCode.AUTH_FORBIDDEN);
    }

    // Keep the legacy lock order: resolved first, then resolving.
    lockMemory(resolvedMemoryId);
    lockMemory(resolvingMemoryId);
    relationWriter.create(
        new EntityRelationCommand(
            resolved.workspaceId(),
            "MEMORY",
            resolvingMemoryId,
            "RESOLVES",
            "MEMORY",
            resolvedMemoryId));
    entityManager
        .createNativeQuery(
            "UPDATE confirmed_memories SET status='ARCHIVED', updated_at=:now WHERE id=:id")
        .setParameter("now", Instant.now())
        .setParameter("id", resolvedMemoryId)
        .executeUpdate();
    outboxAppender.append(
        resolved.workspaceId(),
        "memory",
        resolvedMemoryId.toString(),
        EVENT_UPDATED,
        Map.of("memory_id", resolvedMemoryId.toString()));
  }

  @Override
  @Transactional
  public void reject(UUID candidateId, UUID userId, String reason) {
    MemoryCandidateSnapshot candidate = lockCandidate(candidateId, userId);
    Instant now = Instant.now();
    entityManager
        .createNativeQuery(
            "UPDATE memory_candidates SET status='REJECTED', reviewed_at=:now, reviewed_by_user_id=:userId, rejection_reason=:reason, updated_at=:now WHERE id=:id")
        .setParameter("now", now)
        .setParameter("userId", userId)
        .setParameter("reason", emptyToNull(reason))
        .setParameter("id", candidateId)
        .executeUpdate();
    insertReview(candidate.workspaceId(), candidateId, "REJECT", userId, null, reason, null);
  }

  @Override
  @Transactional
  public void merge(UUID candidateId, UUID targetMemoryId, UUID userId) {
    MemoryCandidateSnapshot candidate = lockCandidate(candidateId, userId);
    UUID targetWorkspace =
        entityManager
            .createQuery(
                "select m.workspaceId from ConfirmedMemory m where m.id=:id and m.deletedAt is null",
                UUID.class)
            .setParameter("id", targetMemoryId)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(MemoryErrorCode.MEMORY_NOT_FOUND));
    if (!candidate.workspaceId().equals(targetWorkspace))
      throw new BusinessException(CommonErrorCode.AUTH_FORBIDDEN);
    lockMemory(targetMemoryId);
    entityManager
        .createNativeQuery(
            "UPDATE memory_candidates SET status='MERGED', reviewed_at=:now, reviewed_by_user_id=:userId, updated_at=:now WHERE id=:id")
        .setParameter("now", Instant.now())
        .setParameter("userId", userId)
        .setParameter("id", candidateId)
        .executeUpdate();
    insertReview(candidate.workspaceId(), candidateId, "MERGE", userId, null, null, targetMemoryId);
  }

  @Override
  @Transactional
  public void expire(UUID candidateId, UUID userId) {
    MemoryCandidateSnapshot candidate = lockCandidate(candidateId, userId);
    entityManager
        .createNativeQuery(
            "UPDATE memory_candidates SET status='EXPIRED', reviewed_at=:now, reviewed_by_user_id=:userId, updated_at=:now WHERE id=:id")
        .setParameter("now", Instant.now())
        .setParameter("userId", userId)
        .setParameter("id", candidateId)
        .executeUpdate();
    insertReview(candidate.workspaceId(), candidateId, "EXPIRE", userId, null, null, null);
  }

  private MemoryCandidateSnapshot lockCandidate(UUID id, UUID userId) {
    var candidateRows =
        entityManager
            .createNativeQuery(
                "SELECT workspace_id, title, summary, body, status FROM memory_candidates WHERE id=:id FOR UPDATE")
            .setParameter("id", id)
            .getResultList();
    if (candidateRows.isEmpty()) {
      throw new BusinessException(MemoryErrorCode.MEMORY_CANDIDATE_NOT_FOUND);
    }
    Object rawCandidate = candidateRows.getFirst();
    Object[] values = (Object[]) rawCandidate;
    MemoryCandidateSnapshot candidate =
        new MemoryCandidateSnapshot(
            (UUID) values[0],
            (String) values[1],
            (String) values[2],
            (String) values[3],
            (String) values[4]);
    if (!PROPOSED.equals(candidate.status()))
      throw new BusinessException(MemoryErrorCode.MEMORY_INVALID_STATE);
    if (!workspaceAccess.isMember(candidate.workspaceId(), userId))
      throw new BusinessException(CommonErrorCode.AUTH_FORBIDDEN);
    return candidate;
  }

  private void lockMemory(UUID id) {
    var memoryRows =
        entityManager
            .createNativeQuery(
                "SELECT id FROM confirmed_memories WHERE id=:id AND deleted_at IS NULL FOR UPDATE")
            .setParameter("id", id)
            .getResultList();
    if (memoryRows.isEmpty()) {
      throw new BusinessException(MemoryErrorCode.MEMORY_NOT_FOUND);
    }
  }

  private MemoryReference findMemoryReference(UUID id) {
    var rows =
        entityManager
            .createNativeQuery(
                "SELECT workspace_id, status FROM confirmed_memories "
                    + "WHERE id=:id AND deleted_at IS NULL")
            .setParameter("id", id)
            .getResultList();
    if (rows.isEmpty()) {
      throw new BusinessException(MemoryErrorCode.MEMORY_NOT_FOUND);
    }
    Object[] values = (Object[]) rows.getFirst();
    return new MemoryReference((UUID) values[0]);
  }

  private void insertReview(
      UUID workspaceId,
      UUID candidateId,
      String action,
      UUID userId,
      CandidateEdits edits,
      String reason,
      UUID targetId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO review_actions (workspace_id, candidate_id, action_type, reviewer_user_id, edited_title, edited_summary, edited_body, rejection_reason, merge_target_memory_id) VALUES (:workspaceId,:candidateId,:action,:userId,:title,:summary,:body,:reason,:target)")
        .setParameter("workspaceId", workspaceId)
        .setParameter("candidateId", candidateId)
        .setParameter("action", action)
        .setParameter("userId", userId)
        .setParameter("title", edits == null ? null : emptyToNull(edits.title()))
        .setParameter("summary", edits == null ? null : emptyToNull(edits.summary()))
        .setParameter("body", edits == null ? null : emptyToNull(edits.body()))
        .setParameter("reason", emptyToNull(reason))
        .setParameter("target", targetId)
        .executeUpdate();
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private record MemoryCandidateSnapshot(
      UUID workspaceId, String title, String summary, String body, String status) {}

  private record MemoryReference(UUID workspaceId) {}
}
