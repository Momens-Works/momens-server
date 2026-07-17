package works.momens.server.project.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import works.momens.server.project.BoardTask;
import works.momens.server.project.TaskReader;
import works.momens.server.project.TaskStatus;

/**
 * 진행률 계산 규칙 검증.
 *
 * <p>DB 없이 계산 규칙(분모에 드는 상태, 소수점 버림, 없는 project 처리)만 확인합니다. 실제 태스크 조회까지 포함한 동작은 app 통합테스트에서 전체 컨텍스트로
 * 검증합니다. task 조회 구현이 package-private라 project 슬라이스에서 조립하기 어렵기 때문입니다({@code TaskCreatorImplTest}와 같은
 * 제약).
 */
@ExtendWith(MockitoExtension.class)
class ProjectReaderImplTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();

  @Mock private ProjectRepository projectRepository;
  @Mock private TaskReader taskReader;
  @InjectMocks private ProjectReaderImpl projectReader;

  @Test
  void progressOfCountsDoneAgainstEveryStatusExceptCancelled() {
    liveProject();
    when(taskReader.listTasksByStatus(eq(PROJECT_ID), any())).thenReturn(List.of());

    projectReader.progressOf(PROJECT_ID);

    // cancelled를 목록에 넣지 않아서 분모에서 빠진다. 이 단언이 그 설계 의도를 고정한다.
    // 상태가 추가되면 PROGRESS_STATUSES도 함께 변경되어 이 테스트가 실패한다.
    // 새 상태가 계산 대상에서 빠지는 것을 바로 확인할 수 있으므로,
    // 분모에 포함할지 결정한 뒤 기대값을 수정한다.
    ArgumentCaptor<List<String>> statuses = ArgumentCaptor.captor();
    verify(taskReader).listTasksByStatus(eq(PROJECT_ID), statuses.capture());
    assertThat(statuses.getValue()).containsExactly("backlog", "todo", "in_progress", "done");
    assertThat(statuses.getValue()).doesNotContain(TaskStatus.CANCELLED.value());
  }

  @Test
  void progressOfReturnsDoneRatioOfTheSameList() {
    liveProject();
    stubTasks(2, 81);

    assertThat(projectReader.progressOf(PROJECT_ID)).hasValue(2);
  }

  @Test
  void progressOfReturnsHundredOnlyWhenEveryCountedTaskIsDone() {
    liveProject();
    stubTasks(5, 0);

    assertThat(projectReader.progressOf(PROJECT_ID)).hasValue(100);
  }

  @Test
  void progressOfDropsTheFractionRatherThanRoundingUp() {
    liveProject();
    // 199/200은 99.5라 반올림하면 100이 된다. 미완료가 있는데 100을 내리지 않도록 버린다.
    stubTasks(199, 1);

    assertThat(projectReader.progressOf(PROJECT_ID)).hasValue(99);
  }

  @Test
  void progressOfIsZeroForProjectWithoutTasks() {
    liveProject();
    when(taskReader.listTasksByStatus(eq(PROJECT_ID), any())).thenReturn(List.of());

    assertThat(projectReader.progressOf(PROJECT_ID)).hasValue(0);
  }

  @Test
  void progressOfIsEmptyForMissingProjectAndDoesNotReadTasks() {
    when(projectRepository.findByIdAndDeletedAtIsNull(PROJECT_ID)).thenReturn(Optional.empty());

    assertThat(projectReader.progressOf(PROJECT_ID)).isEqualTo(OptionalInt.empty());
    verifyNoInteractions(taskReader);
  }

  private void liveProject() {
    when(projectRepository.findByIdAndDeletedAtIsNull(PROJECT_ID))
        .thenReturn(Optional.of(Project.builder().name("Q2 Activation Readiness").build()));
  }

  private void stubTasks(int done, int notDone) {
    List<BoardTask> tasks = new ArrayList<>();
    IntStream.range(0, done).forEach(index -> tasks.add(task("done")));
    IntStream.range(0, notDone).forEach(index -> tasks.add(task("todo")));
    when(taskReader.listTasksByStatus(eq(PROJECT_ID), any())).thenReturn(tasks);
  }

  private static BoardTask task(String status) {
    return new BoardTask(UUID.randomUUID(), "제목", status, "medium", "backend", Instant.now());
  }
}
