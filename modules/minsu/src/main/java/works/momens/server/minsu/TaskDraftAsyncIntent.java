package works.momens.server.minsu;

/**
 * 비동기 생성 의사의 불투명 표식.
 *
 * <p>구현은 Minsu 내부에만 있고 내용을 읽는 방법을 공개하지 않는다. 호출자는 {@link PreparedTaskDraft}에 담긴 값을 {@link
 * SignalTaskDraftGenerator#enroll}로 되돌려 줄 수만 있다.
 */
public interface TaskDraftAsyncIntent {}
