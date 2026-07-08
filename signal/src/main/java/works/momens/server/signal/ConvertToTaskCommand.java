package works.momens.server.signal;

/**
 * convert-to-task 요청 입력. 세 필드 모두 nullable이다.
 *
 * <p>{@code title}이 없으면 Signal 제목으로, {@code priority}가 없으면 {@code medium}으로 폴백한다. {@code role}은 폴백이
 * 없어 body와 Signal의 minsu task_draft 어디에도 없으면 검증 실패로 취급한다.
 */
public record ConvertToTaskCommand(String title, String role, String priority) {}
