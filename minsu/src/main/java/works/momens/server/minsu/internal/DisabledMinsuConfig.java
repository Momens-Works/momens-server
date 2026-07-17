package works.momens.server.minsu.internal;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import works.momens.server.common.api.BusinessException;
import works.momens.server.minsu.Minsu;
import works.momens.server.minsu.MinsuErrorCode;
import works.momens.server.minsu.MinsuSignalContext;
import works.momens.server.minsu.MinsuTaskDraft;

/**
 * 민수 비활성 환경(예: local/test, Vertex 미배선)용 폴백 배선.
 *
 * <p>민수는 하드 의존이라 목값으로 대신하지 않고, 소비자가 draft/suggestion을 요구하면 {@link
 * MinsuErrorCode#MINSU_UNAVAILABLE}로 실패시킵니다. {@link Minsu} 빈이 항상 존재하도록 해 signal 등 소비 모듈이 어느 프로필에서든
 * 부팅되게 하고, 실제 호출 시점에만 명시적 에러를 냅니다.
 *
 * <p>{@link MinsuConfig}와 같은 프로퍼티({@code momens.minsu.enabled})로 상호 배타 배선해 정확히 하나의 {@link Minsu} 빈만
 * 등록되게 합니다(활성이면 실제 빈, 아니면 이 폴백). {@code @ConditionalOnMissingBean} 순서 의존을 피합니다.
 */
@Configuration
@ConditionalOnProperty(name = "momens.minsu.enabled", havingValue = "false", matchIfMissing = true)
class DisabledMinsuConfig {

  @Bean
  Minsu disabledMinsu() {
    return new DisabledMinsu();
  }

  private static final class DisabledMinsu implements Minsu {

    @Override
    public MinsuTaskDraft draftTask(MinsuSignalContext context) {
      throw unavailable();
    }

    @Override
    public String suggest(MinsuSignalContext context) {
      throw unavailable();
    }

    private static BusinessException unavailable() {
      return new BusinessException(
          MinsuErrorCode.MINSU_UNAVAILABLE, Map.of("reason", "minsu_disabled"));
    }
  }
}
