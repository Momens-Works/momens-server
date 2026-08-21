package works.momens.server.web.memory;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import works.momens.server.memory.MemoryWriter;
import works.momens.server.web.memory.dto.response.ConfirmedMemoryResponse;

@Service
@RequiredArgsConstructor
class MemoryWriteService {
  private final MemoryWriter memoryWriter;

  ConfirmedMemoryResponse confirm(UUID candidateId, UUID userId) {
    return ConfirmedMemoryResponse.from(memoryWriter.confirm(candidateId, userId));
  }

  ConfirmedMemoryResponse editAndConfirm(
      UUID candidateId, UUID userId, String title, String summary, String body) {
    return ConfirmedMemoryResponse.from(
        memoryWriter.editAndConfirm(
            candidateId, userId, new MemoryWriter.CandidateEdits(title, summary, body)));
  }

  void reject(UUID candidateId, UUID userId, String reason) {
    memoryWriter.reject(candidateId, userId, reason);
  }

  void merge(UUID candidateId, UUID userId, UUID targetMemoryId) {
    memoryWriter.merge(candidateId, targetMemoryId, userId);
  }

  void expire(UUID candidateId, UUID userId) {
    memoryWriter.expire(candidateId, userId);
  }

  void resolve(UUID resolvedMemoryId, UUID resolvingMemoryId, UUID userId) {
    memoryWriter.resolve(resolvedMemoryId, resolvingMemoryId, userId);
  }
}
