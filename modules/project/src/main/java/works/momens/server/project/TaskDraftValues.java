package works.momens.server.project;

/**
 * draft 반영이 비교하고 기록하는 세 필드.
 *
 * <p>baseline과 새 draft가 같은 모양이라 한 타입으로 둡니다. 명령이 문자열 여섯 개를 나란히 받으면 어느 셋이 baseline인지 호출부에서 보이지 않고,
 * 순서를 틀려도 컴파일됩니다.
 *
 * <p>{@code role}과 {@code priority}는 {@code tasks}가 저장하는 문자열 값 그대로입니다. 허용값은 DB CHECK 제약이 지킵니다.
 */
public record TaskDraftValues(String title, String role, String priority) {}
