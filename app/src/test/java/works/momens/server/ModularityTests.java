package works.momens.server;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Spring Modulith 경계 검증 테스트.
 *
 * <p>도메인 모듈이 추가되면 모듈 간 의존 방향과 경계를 이 테스트가 검증합니다. 초기 skeleton 단계에서는 도메인 모듈이 아직 없으므로 검증 대상 모듈이 비어 있어도
 * 통과합니다.
 */
class ModularityTests {

  static final ApplicationModules modules = ApplicationModules.of(MomensServerApplication.class);

  @Test
  void verifiesModuleStructure() {
    modules.verify();
  }
}
