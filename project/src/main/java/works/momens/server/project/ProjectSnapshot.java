package works.momens.server.project;

import java.time.LocalDate;
import java.util.UUID;

/**
 * project 한 건의 조회 결과.
 *
 * <p>모바일 API가 필요로 하는 최소 read 모델입니다. bootstrap(MOM-60)은 id와 name을, 브리프의 프로젝트 스냅샷은 name과 targetDate와
 * progress와 summary를 사용합니다. 멤버 role은 project가 아니라 workspace 멤버십이 소유하므로 여기 두지 않습니다.
 */
public record ProjectSnapshot(
    UUID id, UUID workspaceId, String name, LocalDate targetDate, int progress, String summary) {}
