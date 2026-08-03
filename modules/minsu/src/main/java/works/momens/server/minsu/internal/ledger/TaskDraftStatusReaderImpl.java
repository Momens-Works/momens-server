package works.momens.server.minsu.internal.ledger;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.minsu.DraftStatus;
import works.momens.server.minsu.TaskDraftStatusReader;

/**
 * 읽기 시점 deadline 투영(docs/design/minsu-async-task-draft-design.md 8.6절).
 *
 * <p>판정은 DB 한 번의 비교로 끝난다. 상한을 코드에서 계산하지 않는 이유는 원장에 저장된 {@code read_deadline_at}과 같은 시계를 써야 하기 때문이다.
 * 애플리케이션 시계로 비교하면 인스턴스 간 시계 편차가 그대로 상태 판정 편차가 된다.
 */
@Component
class TaskDraftStatusReaderImpl implements TaskDraftStatusReader {

  private final TaskDraftGenerationRepository repository;

  TaskDraftStatusReaderImpl(TaskDraftGenerationRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public DraftStatus statusOf(UUID taskId) {
    return repository.existsGenerating(taskId) ? DraftStatus.GENERATING : DraftStatus.READY;
  }
}
