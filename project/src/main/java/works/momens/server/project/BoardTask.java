package works.momens.server.project;

import java.util.UUID;

/**
 * 보드 조회용 태스크 한 건.
 *
 * <p>{@code status}와 {@code priority}는 저장된 원본 문자열입니다. 보드 그룹(todo/in_progress/done) 매핑과 모바일 priority
 * 매핑(urgent를 high로 반환 등)은 조회하는 쪽(mobile)이 정합니다.
 */
public record BoardTask(UUID id, String title, String status, String priority, String role) {}
