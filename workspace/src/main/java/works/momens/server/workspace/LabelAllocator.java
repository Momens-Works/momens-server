package works.momens.server.workspace;

import java.util.UUID;

/**
 * workspace 모듈의 라벨 발급 public API.
 *
 * <p>{@code project} 모듈이 task를 만들 때 이 인터페이스로 {@code MOM} 라벨을 발급받습니다(MOM-37). 동기 반환값이 필요한 협력이라
 * application event가 아니라 public API 직접 참조로 둡니다(docs/rules/architecture.md). 발급 카운터 테이블과 repository는
 * 하위 도메인 {@code label}에 숨깁니다.
 *
 * <p>지금은 {@code MOM}만 발급합니다. {@code SUG}/{@code MEM}은 memory 모듈 작업이라 그때 별도 메서드로 엽니다.
 */
public interface LabelAllocator {

  /**
   * workspace 범위에서 다음 {@code MOM} 라벨을 발급합니다. 형식은 {@code MOM-0001}이고 workspace마다 1부터 독립적으로 증가합니다. 같은
   * workspace에 동시 발급이 들어와도 번호가 겹치지 않습니다.
   */
  String allocateMomLabel(UUID workspaceId);
}
