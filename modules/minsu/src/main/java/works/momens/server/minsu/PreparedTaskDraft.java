package works.momens.server.minsu;

/**
 * {@link SignalTaskDraftGenerator#prepare} 결과(docs/design/minsu-async-task-draft-design.md 5.5절).
 *
 * <p>{@code draft}는 호출자가 {@code tasks}에 쓰고, {@code asyncIntent}는 해석하지 않고 {@link
 * SignalTaskDraftGenerator#enroll}에 그대로 넘긴다. 비동기가 비활성이면 {@code null}이지만 호출자가 그것을 알 필요는 없다. 존재 여부로
 * 분기하기 시작하면 판정 소유권이 둘로 나뉘고, 그 순간 한 요청 안에서 판정이 갈리는 경로가 열린다.
 */
public record PreparedTaskDraft(TaskDraft draft, TaskDraftAsyncIntent asyncIntent) {}
