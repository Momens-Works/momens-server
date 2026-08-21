package works.momens.server.memory;

import java.util.UUID;

/** 웹 Product API가 사용하는 memory write public API입니다. */
public interface MemoryWriter {

  ConfirmedMemoryDetail confirm(UUID candidateId, UUID userId, CandidateEdits edits);

  void reject(UUID candidateId, UUID userId, String reason);

  void merge(UUID candidateId, UUID targetMemoryId, UUID userId);

  void expire(UUID candidateId, UUID userId);

  ConfirmedMemoryDetail editAndConfirm(UUID candidateId, UUID userId, CandidateEdits edits);

  void resolve(UUID resolvedMemoryId, UUID resolvingMemoryId, UUID userId);

  record CandidateEdits(String title, String summary, String body) {}
}
