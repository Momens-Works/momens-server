package works.momens.server.web.memory;

import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.memory.ConfirmedMemoryDetail;
import works.momens.server.memory.MemoryErrorCode;
import works.momens.server.web.dto.response.WebMessageResponse;
import works.momens.server.web.memory.dto.request.EditAndConfirmMemoryCandidateRequest;
import works.momens.server.web.memory.dto.request.MergeMemoryCandidateRequest;
import works.momens.server.web.memory.dto.request.RejectMemoryCandidateRequest;
import works.momens.server.web.memory.dto.request.ResolveMemoryRequest;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class MemoryWriteController implements MemoryWriteControllerDocs {
  private final MemoryWriteService memoryWriteService;

  @PostMapping(path = "/memory-candidates/{candidateId}/confirm", version = "1")
  @ResponseStatus(HttpStatus.CREATED)
  public ConfirmedMemoryDetail confirm(@PathVariable UUID candidateId, Principal principal) {
    return memoryWriteService.confirm(candidateId, CurrentUser.id(principal));
  }

  @PostMapping(path = "/memory-candidates/{candidateId}/edit-and-confirm", version = "1")
  @ResponseStatus(HttpStatus.CREATED)
  public ConfirmedMemoryDetail editAndConfirm(
      @PathVariable UUID candidateId,
      @RequestBody EditAndConfirmMemoryCandidateRequest request,
      Principal principal) {
    return memoryWriteService.editAndConfirm(
        candidateId, CurrentUser.id(principal), request.title(), request.summary(), request.body());
  }

  @PostMapping(path = "/memory-candidates/{candidateId}/reject", version = "1")
  public WebMessageResponse reject(
      @PathVariable UUID candidateId,
      @RequestBody(required = false) RejectMemoryCandidateRequest request,
      Principal principal) {
    memoryWriteService.reject(
        candidateId, CurrentUser.id(principal), request == null ? null : request.reason());
    return new WebMessageResponse("rejected");
  }

  @PostMapping(path = "/memory-candidates/{candidateId}/merge", version = "1")
  public WebMessageResponse merge(
      @PathVariable UUID candidateId,
      @RequestBody MergeMemoryCandidateRequest request,
      Principal principal) {
    if (request.targetMemoryId() == null) {
      throw new BusinessException(MemoryErrorCode.MEMORY_INVALID_INPUT);
    }
    memoryWriteService.merge(candidateId, CurrentUser.id(principal), request.targetMemoryId());
    return new WebMessageResponse("merged");
  }

  @PostMapping(path = "/memory-candidates/{candidateId}/expire", version = "1")
  public WebMessageResponse expire(@PathVariable UUID candidateId, Principal principal) {
    memoryWriteService.expire(candidateId, CurrentUser.id(principal));
    return new WebMessageResponse("expired");
  }

  @PostMapping(path = "/memories/{memoryId}/resolve", version = "1")
  public WebMessageResponse resolve(
      @PathVariable UUID memoryId, @RequestBody ResolveMemoryRequest request, Principal principal) {
    if (request.resolvingMemoryId() == null) {
      throw new BusinessException(MemoryErrorCode.MEMORY_INVALID_INPUT);
    }
    memoryWriteService.resolve(memoryId, request.resolvingMemoryId(), CurrentUser.id(principal));
    return new WebMessageResponse("resolved");
  }
}
