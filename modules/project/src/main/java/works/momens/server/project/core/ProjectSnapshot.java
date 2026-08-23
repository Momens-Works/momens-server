package works.momens.server.project.core;

import java.time.LocalDate;
import java.util.UUID;

/**
 * project 한 건의 조회 결과.
 *
 * <p>모바일 API가 필요로 하는 최소 read 모델입니다. bootstrap(MOM-60)은 id와 name을, 브리프의 프로젝트 스냅샷은 name과 targetDate와
 * summary를 사용합니다. 멤버 role은 workspace 멤버십에서 관리하므로 여기 두지 않습니다.
 *
 * <p>진행률은 포함하지 않습니다. 진행률은 태스크를 기준으로 계산하며(MOM-0800, ADR-0013), {@code projects}에 저장된 값을 사용하지 않습니다.
 * 필요한 곳에서는 {@link TaskProgressReader#progressOf(UUID)}로 조회합니다.
 */
public record ProjectSnapshot(
    UUID id, UUID workspaceId, String name, LocalDate targetDate, String summary) {}
