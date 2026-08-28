package works.momens.server.memory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 확정 메모리 생성에 필요한 입력값입니다.
 *
 * <p>후보 단계를 거치지 않고 확정 메모리를 생성하는 경로에서만 사용합니다. 레거시가 워크스페이스 생성 시 함께 저장하는 메모리 세 건이 해당 경로를 사용합니다.
 *
 * <p>{@code summary}와 {@code sourceRefIds}는 입력받지 않습니다. 현재 해당 API를 호출하는 유일한 경로에서는 두 값을 저장하지 않습니다. 두
 * 값을 사용하던 레거시 수동 생성(H043)은 호출하는 클라이언트가 없어 이관 대상에서 제외되었습니다(MOM-0901). 필요한 호출 경로가 추가되면 입력값도 함께 확장합니다.
 *
 * <p>{@code metadata}는 JSONB 컬럼에 저장하며, 값이 없으면 NULL로 저장합니다.
 */
public record CreateConfirmedMemoryCommand(
    UUID workspaceId,
    UUID confirmedByUserId,
    String memoryType,
    String title,
    String body,
    List<UUID> relatedEntityIds,
    Map<String, Object> metadata) {}
