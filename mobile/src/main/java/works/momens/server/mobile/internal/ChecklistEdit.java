package works.momens.server.mobile.internal;

import java.util.UUID;

/**
 * 완료기준 수정 입력 한 항목.
 *
 * <p>presentation이 project 도메인의 command 타입을 알지 않도록, 완료기준 수정 입력을 mobile이 소유하는 형태로 받습니다. 조합 서비스가 이 값을
 * project의 수정 command로 변환합니다(생성 흐름에서 컨트롤러가 원시값만 넘기고 서비스가 command를 조립하는 방식과 같습니다). id가 있으면 기존 항목,
 * 없으면 새 항목이며, {@code completed}는 수정 화면이 저장한 완료 상태입니다.
 */
public record ChecklistEdit(UUID id, String title, boolean completed) {}
